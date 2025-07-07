package thong.test.customerpointservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thong.test.customerpointservice.entities.PointEntity;
import thong.test.customerpointservice.entities.TransactionLogEntity;
import thong.test.customerpointservice.infrastructure.RedisLockService;
import thong.test.customerpointservice.pojo.CreateUserPointRequest;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.pojo.dto.TransferPointMetaData;
import thong.test.customerpointservice.repository.PointRepository;
import thong.test.customerpointservice.repository.TransactionLogRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPointService {

    private final PointRepository pointRepository;

    private final TransactionLogRepository transactionLogRepository;

    private final RedisLockService redisLockService;

    @Transactional(readOnly = true)
    public PointEntity findByUserId(Long userId) {
        return pointRepository.findByUserId(userId).orElse(null);
    }

    @Transactional
    public PointEntity save(CreateUserPointRequest request) {
        var userBonusPointEntity = new PointEntity();
        userBonusPointEntity.setUserId(request.getUserId());
        userBonusPointEntity.setCurrentPoint(request.getCurrentPoint());
        try {
            return pointRepository.save(userBonusPointEntity);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    @Transactional
    public void transferPoint(ModifyPointEventRecord<TransferPointMetaData> event) {
        String txnId = event.getTransactionId();
        Long senderId = event.getUserId();
        Long receiverId = event.getEventMetaData().getReceiverUserId();
        int amount = event.getEventMetaData().getTransferredAmount();

        validateTransferRequest(txnId, senderId, receiverId, amount);

        if (isTransactionProcessed(txnId)) {
            log.warn("Transaction {} was already processed", txnId);
            return;
        }

        String lockKey1 = "user_point:" + Math.min(senderId, receiverId);
        String lockKey2 = "user_point:" + Math.max(senderId, receiverId);

        TransactionLogEntity transactionLog = createTransactionLog(txnId, senderId, receiverId, amount);
        transactionLogRepository.save(transactionLog);

        try {
            boolean senderLockAcquired = redisLockService.tryLockWithRetry(lockKey1, txnId, 10L, TimeUnit.SECONDS, 5, 100);

            if (!senderLockAcquired) {
                transactionLog.setStatus("FAILED");
                throw new RuntimeException("Could not acquire lock for sender account");
            }

            boolean receiverLockAcquired = redisLockService.tryLockWithRetry(lockKey2, txnId, 10L, TimeUnit.SECONDS, 5, 100);

            if (!receiverLockAcquired) {
                transactionLog.setStatus("FAILED");
                throw new RuntimeException("Could not acquire lock for receiver account");
            }

            PointEntity sender = pointRepository.findByUserId(senderId)
                    .orElseThrow(() -> new RuntimeException("Sender account not found"));

            PointEntity receiver = pointRepository.findByUserId(receiverId)
                    .orElseThrow(() -> new RuntimeException("Receiver account not found"));

            if (sender.getCurrentPoint() < amount) {
                transactionLog.setStatus("FAILED");
                throw new RuntimeException("User " + sender.getUserId() + " Don't have enough point: " + sender.getCurrentPoint());
            }

            try {
                sender.setCurrentPoint(sender.getCurrentPoint() - amount);
                receiver.setCurrentPoint(receiver.getCurrentPoint() + amount);

                pointRepository.saveAll(List.of(sender, receiver));

                transactionLog.setStatus("SUCCESS");

                transactionLogRepository.save(transactionLog);

                log.info("Successfully transferred {} points from {} to {}, transaction: {}",
                        amount, senderId, receiverId, txnId);

            } catch (Exception e) {
                log.error("Error during transfer: {}", e.getMessage());
                transactionLog.setStatus("FAILED");
                throw e;
            }

        } finally {
            redisLockService.unlock(lockKey2, txnId);
            redisLockService.unlock(lockKey1, txnId);
        }
    }

    private void validateTransferRequest(String txnId, Long senderId, Long receiverId, int amount) {
        if (txnId == null || txnId.isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be empty");
        }
        if (senderId == null || receiverId == null) {
            throw new IllegalArgumentException("Sender and receiver IDs cannot be null");
        }
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Sender and receiver cannot be the same");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
    }

    private boolean isTransactionProcessed(String txnId) {
        return transactionLogRepository.findByTransactionId(txnId)
                .map(log -> log.getStatus().equals("SUCCESS"))
                .orElse(false);
    }

    private TransactionLogEntity createTransactionLog(String txnId, Long senderId, Long receiverId, int amount) {
        return TransactionLogEntity.builder()
                .transactionId(txnId)
                .senderId(senderId)
                .receiverId(receiverId)
                .amount(amount)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
    }

}
