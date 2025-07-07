package thong.test.customerpointservice.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock:";

    private static final long DEFAULT_EXPIRE_SECONDS = 5;

    public boolean tryLock(String key, String value, Long ttl, TimeUnit unit) {

        if (ttl == null) {
            ttl = DEFAULT_EXPIRE_SECONDS;
        }


        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, ttl, unit);
        return Boolean.TRUE.equals(success);
    }

    public void unlock(String lockKey, String value) {
        String key = LOCK_PREFIX + lockKey;
        String currentValue = redisTemplate.opsForValue().get(key);
        if (value.equals(currentValue)) {
            redisTemplate.delete(key);
        }
    }

    public boolean tryLockWithRetry(String lockKey, String value, Long ttl, TimeUnit unit, int maxRetries, long initialDelayMs) {
        String key = LOCK_PREFIX + lockKey;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (tryLock(key, value, ttl, unit)) {
                return true;
            }

            if (attempt < maxRetries - 1) {
                try {
                    log.info("Failed to acquire lock, retrying in {} ms", initialDelayMs);

                    // Thuật toán Exponential Backoff
                    long delay = initialDelayMs * (long) Math.pow(2, attempt);
                    Thread.sleep(Math.min(delay, 5000));
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting to acquire lock", e);
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
