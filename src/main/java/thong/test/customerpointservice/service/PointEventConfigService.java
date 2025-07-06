package thong.test.customerpointservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import thong.test.customerpointservice.entities.PointEventConfigEntity;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;
import thong.test.customerpointservice.repository.PointEventConfigRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PointEventConfigService {

    private final PointEventConfigRepository configRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CONFIG_CACHE_KEY_PREFIX = "point:config:";

    public Optional<PointEventConfigEntity> getConfig(ModifyPointTypeEnum eventType) {
        String cacheKey = CONFIG_CACHE_KEY_PREFIX + eventType.name();

        // 1. Đọc từ Redis
        List<PointEventConfigEntity> cachedConfigs = configRepository.findValidConfigs(eventType, LocalDateTime.now());

        return cachedConfigs.stream()
                .filter(c -> eventType.equals(c.getEventType()))
                .findFirst();
    }

    private boolean isConditionMatch(String conditionExpr, Map<String, Object> context) {
        if (conditionExpr == null || conditionExpr.isBlank()) {
            return true; // Nếu không có điều kiện → luôn hợp lệ
        }

        // Giả lập đánh giá điều kiện với SpEL (hoặc viết parser riêng)
        try {
            var parser = new SpelExpressionParser();
            var spelContext = new StandardEvaluationContext();
            context.forEach(spelContext::setVariable);
            Boolean result = parser.parseExpression(conditionExpr).getValue(spelContext, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // Log lỗi nếu cần
            return false;
        }
    }
}
