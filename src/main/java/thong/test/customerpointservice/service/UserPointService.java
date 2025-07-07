package thong.test.customerpointservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thong.test.customerpointservice.entities.TransactionLogEntity;
import thong.test.customerpointservice.entities.PointEntity;
import thong.test.customerpointservice.infrastructure.RedisLockService;
import thong.test.customerpointservice.pojo.CreateUserPointRequest;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.pojo.dto.TransferPointMetaData;
import thong.test.customerpointservice.repository.TransactionLogRepository;
import thong.test.customerpointservice.repository.PointRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

    @Transactional(noRollbackFor = ObjectOptimisticLockingFailureException.class)
    @Retryable(
        retryFor =  ObjectOptimisticLockingFailureException.class,
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000)
    )
    public void transferPoint(ModifyPointEventRecord<TransferPointMetaData> event) {
        String txnId = event.getTransactionId();
        Long senderId = event.getUserId();
        Long receiverId = event.getEventMetaData().getReceiverUserId();
        int amount = event.getEventMetaData().getTransferredAmount();

        if (amount <= 0) {
            log.error("Invalid point amount");
            return;
        }

        if (senderId.equals(receiverId)) {
            log.error("Cannot transfer points to yourself");
            return;
        }

        PointEntity sender = pointRepository.findByUserId(senderId);
        PointEntity receiver = pointRepository.findByUserId(receiverId);

        if (sender.getCurrentPoint() < amount) {
            log.error("Sender does not have enough points.");
            return;
        }

        sender.setCurrentPoint(sender.getCurrentPoint() - amount);
        receiver.setCurrentPoint(receiver.getCurrentPoint() + amount);

        pointRepository.saveAll(List.of(sender, receiver));

        var transactionLog = TransactionLogEntity.builder()
                .transactionId(txnId)
                .senderId(senderId)
                .receiverId(receiverId)
                .amount(amount)
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

        transactionLogRepository.save(transactionLog);

        log.info("Transferred {} points from {} to {}", amount, senderId, receiverId);
    }

    @Recover
    public void recover(ObjectOptimisticLockingFailureException ex,
                        ModifyPointEventRecord<TransferPointMetaData> event) {
        log.error("Retry failed for transactionId={}, sender={}, receiver={}, reason={}",
                event.getTransactionId(),
                event.getUserId(),
                event.getEventMetaData().getReceiverUserId(),
                ex.getMessage());
    }

}
