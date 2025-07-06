package thong.test.customerpointservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thong.test.customerpointservice.entities.TransactionLogEntity;
import thong.test.customerpointservice.entities.UserBonusPointEntity;
import thong.test.customerpointservice.pojo.CreateUserPointRequest;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.pojo.dto.TransferPointMetaData;
import thong.test.customerpointservice.repository.TransactionLogRepository;
import thong.test.customerpointservice.repository.UserBonusPointRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPointService {

    private final UserBonusPointRepository userBonusPointRepository;

    private final TransactionLogRepository transactionLogRepository;

    @Transactional(readOnly = true)
    public UserBonusPointEntity  findByUserId(Long userId) {
        return userBonusPointRepository.findByUserId(userId);
    }

    @Transactional
    public UserBonusPointEntity save(CreateUserPointRequest request) {
        var userBonusPointEntity = new UserBonusPointEntity();
        userBonusPointEntity.setUserId(request.getUserId());
        userBonusPointEntity.setCurrentPoint(request.getCurrentPoint());
        try {
            return userBonusPointRepository.save(userBonusPointEntity);
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

        if (amount <= 0) {
            log.error("Invalid point amount");
            return;
        }

        if (senderId.equals(receiverId)) {
            log.error("Cannot transfer points to yourself");
            return;
        }

        if (transactionLogRepository.existsByTransactionId(txnId)) {
            log.info("Transaction {} already processed, skipping", txnId);
            return;
        }

        List<Long> orderedIds = Stream.of(senderId, receiverId)
                .sorted()
                .toList();

        Map<Long, UserBonusPointEntity> lockedUsers = userBonusPointRepository
                .findByIdsForUpdate(orderedIds).stream()
                .collect(Collectors.toMap(
                        UserBonusPointEntity::getUserId,
                        Function.identity()
                ));

        UserBonusPointEntity sender = lockedUsers.get(senderId);
        UserBonusPointEntity receiver = lockedUsers.get(receiverId);

        if (sender.getCurrentPoint() < amount) {
            log.error("Sender does not have enough points.");
            return;
        }

        sender.setCurrentPoint(sender.getCurrentPoint() - amount);
        receiver.setCurrentPoint(receiver.getCurrentPoint() + amount);

        userBonusPointRepository.saveAll(List.of(sender, receiver));

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

}
