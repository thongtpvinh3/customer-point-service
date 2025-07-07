package thong.test.customerpointservice.service.modify_point_strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;
import thong.test.customerpointservice.infrastructure.RedisLockService;
import thong.test.customerpointservice.pojo.CreateUserPointRequest;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.pojo.dto.CreateNewUserMetaData;
import thong.test.customerpointservice.service.PointEventConfigService;
import thong.test.customerpointservice.service.UserPointService;
import thong.test.customerpointservice.service.modify_point_strategy.ModifyPointStrategy;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateNewStrategy implements ModifyPointStrategy<CreateNewUserMetaData> {

    private final PointEventConfigService pointEventConfigService;

    private final UserPointService userPointService;

    private final RedisLockService redisLockService;

    @Override
    public int getPoint(ModifyPointEventRecord<CreateNewUserMetaData> event) {
        var config = getEventConfig(pointEventConfigService, ModifyPointTypeEnum.CREATE_NEW);
        return config.getPointValue();
    }

    @Override
    @Transactional
    public void doModifyPoint(ModifyPointEventRecord<CreateNewUserMetaData> event) {
        long userId = event.getUserId();
        String lockKey = "lock:create-user-point:" + userId;
        boolean locked = redisLockService.tryLock(lockKey, "lock", 5L, TimeUnit.SECONDS);
        if (!locked) {
            throw new RuntimeException("Another request is processing this user.");
        }

        try {
            var user = userPointService.findByUserId(event.getUserId());
            if (user != null) {
                log.info("==========> user already exists");
                return;
            }
            user = userPointService.save(new CreateUserPointRequest(event.getUserId(), 0L));
            var amount = getPoint(null);
            var currentPoint = user.getCurrentPoint();
            user.setCurrentPoint(currentPoint + amount);
            log.info("==========> created new user point");
        } finally {
            redisLockService.unlock(lockKey, "lock");
        }
    }

    @Override
    public ModifyPointTypeEnum getEventType() {
        return ModifyPointTypeEnum.CREATE_NEW;
    }

}
