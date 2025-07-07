package thong.test.customerpointservice.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock:";

    private static final long DEFAULT_EXPIRE_SECONDS = 5;

    // Lua script để atomic acquire lock
    private static final String ACQUIRE_LOCK_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 0 then " +
                    "  redis.call('hset', KEYS[1], ARGV[1], 1) " +
                    "  redis.call('pexpire', KEYS[1], ARGV[2]) " +
                    "  return 1 " +
                    "elseif redis.call('hexists', KEYS[1], ARGV[1]) == 1 then " +
                    "  return 1 " +
                    "else " +
                    "  local currentLocks = redis.call('hlen', KEYS[1]) " +
                    "  if tonumber(currentLocks) < tonumber(ARGV[3]) then " +
                    "    redis.call('hset', KEYS[1], ARGV[1], 1) " +
                    "    return 1 " +
                    "  end " +
                    "  return 0 " +
                    "end";

    // Lua script để release lock
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then " +
                    "  return 0 " +
                    "else " +
                    "  redis.call('hdel', KEYS[1], ARGV[1]) " +
                    "  if redis.call('hlen', KEYS[1]) == 0 then " +
                    "    redis.call('del', KEYS[1]) " +
                    "  end " +
                    "  return 1 " +
                    "end";

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

    public boolean tryAcquireLock(String lockKey, String requestId, Long timeout, TimeUnit unit) {

        String key = LOCK_PREFIX + lockKey;
        long timeoutMillis = unit.toMillis(timeout);
        long startTime = System.currentTimeMillis();

        try {
            do {
                // Thử set key với NX (Only set if not exists) và thời gian expire
                Boolean locked = redisTemplate.opsForValue()
                        .setIfAbsent(key, requestId, timeout, unit);

                if (Boolean.TRUE.equals(locked)) {
                    return true;
                }

                // Nếu chưa lấy được lock, đợi một chút rồi thử lại
                Thread.sleep(100);
            } while (System.currentTimeMillis() - startTime < timeoutMillis);

            return false;

        } catch (Exception e) {
            log.error("Error while trying to acquire lock: {}", e.getMessage(), e);
            return false;
        }
    }


    public boolean tryReleaseLock(String lockKey, String requestId) {
        String key = LOCK_PREFIX + lockKey;
        try {
            // Lấy giá trị hiện tại của lock
            String currentValue = redisTemplate.opsForValue().get(key);

            // Chỉ xóa nếu requestId khớp với giá trị hiện tại
            if (requestId.equals(currentValue)) {
                return redisTemplate.delete(key);
            }
            return false;

        } catch (Exception e) {
            log.error("Error while releasing lock: {}", e.getMessage(), e);
            return false;
        }

    }
    /**
     * Cố gắng lấy lock với số lượng permits cho phép
     *
     * @param lockKey   Khóa của lock
     * @param requestId ID của request (để đảm bảo chỉ request đã lấy lock mới có thể giải phóng)
     * @param permits   Số lượng lock tối đa cho phép đồng thời
     * @param timeout   Thời gian chờ tối đa
     * @param unit      Đơn vị thời gian
     * @return true nếu lấy được lock, false nếu không
     */
    public boolean tryAcquire(String lockKey, String requestId, int permits, long timeout, TimeUnit unit) {
        try {
            long timeoutMillis = unit.toMillis(timeout);
            long startTime = System.currentTimeMillis();

            do {
                boolean acquired = tryAcquireOnce(lockKey, requestId, permits, timeoutMillis);
                if (acquired) {
                    return true;
                }

                // Nếu chưa lấy được lock, sleep một chút và thử lại
                Thread.sleep(100);
            } while (System.currentTimeMillis() - startTime < timeoutMillis);

            return false;
        } catch (Exception e) {
            log.error("Error acquiring lock: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Thử lấy lock một lần
     */
    private boolean tryAcquireOnce(String lockKey, String requestId, int permits, long timeoutMillis) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(ACQUIRE_LOCK_SCRIPT, Long.class);
            List<String> keys = Collections.singletonList(lockKey);
            Long result = redisTemplate.execute(
                    script,
                    keys,
                    requestId,
                    String.valueOf(timeoutMillis),
                    String.valueOf(permits)
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Error in tryAcquireOnce: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Giải phóng lock
     *
     * @param lockKey   Khóa của lock
     * @param requestId ID của request
     * @return true nếu giải phóng thành công, false nếu không
     */
    public boolean release(String lockKey, String requestId) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
            List<String> keys = Collections.singletonList(lockKey);
            Long result = redisTemplate.execute(script, keys, requestId);
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Error releasing lock: {}", e.getMessage(), e);
            return false;
        }
    }
}
