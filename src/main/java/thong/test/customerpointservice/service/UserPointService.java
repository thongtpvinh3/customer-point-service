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
        return pointRepository.findByUserId(userId);
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

        // 1. Validate input
        validateTransferRequest(txnId, senderId, receiverId, amount);

        // 2. Check if transaction already processed
        if (isTransactionProcessed(txnId)) {
            log.warn("Transaction {} was already processed", txnId);
            return;
        }

        String lockKey1 = String.valueOf(Math.min(senderId, receiverId));
        String lockKey2 = String.valueOf(Math.max(senderId, receiverId));

        try {
            // Acquire locks in order
            boolean senderLockAcquired = redisLockService.tryAcquireLock("user_point:" + lockKey1, txnId + "-1", 5L, TimeUnit.SECONDS);

            if (!senderLockAcquired) {
                throw new RuntimeException("Could not acquire lock for sender account");
            }

            boolean receiverLockAcquired = redisLockService.tryAcquireLock("user_point:" + lockKey2, txnId + "-2", 5L, TimeUnit.SECONDS);

            if (!receiverLockAcquired) {
                throw new RuntimeException("Could not acquire lock for receiver account");
            }

            // 4. Get latest account states
            PointEntity sender = pointRepository.findByUserIdWithPessimisticLock(senderId)
                    .orElseThrow(() -> new RuntimeException("Sender account not found"));

            PointEntity receiver = pointRepository.findByUserIdWithPessimisticLock(receiverId)
                    .orElseThrow(() -> new RuntimeException("Receiver account not found"));

            // 5. Validate balance
            if (sender.getCurrentPoint() < amount) {
                throw new RuntimeException("Insufficient points: " + sender.getCurrentPoint());
            }

            // 6. Perform transfer
            try {
                // Create transaction log first with PENDING status
                TransactionLogEntity transactionLog = createTransactionLog(txnId, senderId, receiverId, amount, "PENDING");
                transactionLogRepository.save(transactionLog);

                // Update points
                sender.setCurrentPoint(sender.getCurrentPoint() - amount);
                receiver.setCurrentPoint(receiver.getCurrentPoint() + amount);

                // Save both entities
                pointRepository.saveAll(List.of(sender, receiver));

                // Update transaction status to SUCCESS
                transactionLog.setStatus("SUCCESS");
                transactionLogRepository.save(transactionLog);

                log.info("Successfully transferred {} points from {} to {}, transaction: {}",
                        amount, senderId, receiverId, txnId);

            } catch (Exception e) {
                log.error("Error during transfer: {}", e.getMessage());
                // Update transaction status to FAILED
                updateTransactionStatus(txnId, "FAILED");
                throw e;
            }

        } finally {
            // Giải phóng lock theo thứ tự ngược lại
            redisLockService.tryReleaseLock(lockKey2, txnId + "-2");
            redisLockService.tryReleaseLock(lockKey1, txnId + "-1");

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

    private TransactionLogEntity createTransactionLog(String txnId, Long senderId, Long receiverId,
                                                      int amount, String status) {
        return TransactionLogEntity.builder()
                .transactionId(txnId)
                .senderId(senderId)
                .receiverId(receiverId)
                .amount(amount)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void updateTransactionStatus(String txnId, String status) {
        transactionLogRepository.findByTransactionId(txnId)
                .ifPresent(log -> {
                    log.setStatus(status);
                    transactionLogRepository.save(log);
                });
    }

}
