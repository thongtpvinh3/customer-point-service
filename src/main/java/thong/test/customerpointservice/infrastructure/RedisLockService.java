package thong.test.customerpointservice.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    private static final long DEFAULT_EXPIRE_SECONDS = 5;

    public boolean tryLock(String key, String value, Long ttl, TimeUnit unit) {
        if (ttl == null) {
            ttl = DEFAULT_EXPIRE_SECONDS;
        }
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, ttl, unit);
        return Boolean.TRUE.equals(success);
    }

    public void unlock(String key, String value) {
        String currentValue = redisTemplate.opsForValue().get(key);
        if (value.equals(currentValue)) {
            redisTemplate.delete(key);
        }
    }
}
