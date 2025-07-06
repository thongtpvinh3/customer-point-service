package thong.test.customerpointservice.service.modify_point_strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;
import thong.test.customerpointservice.pojo.dto.ModifyPointMetaData;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ModifyPointStrategyProvider {

    private final List<ModifyPointStrategy<? extends ModifyPointMetaData>> strategies;

    public ModifyPointStrategy<? extends ModifyPointMetaData> getStrategy(ModifyPointTypeEnum eventType) {
        return strategies.stream()
                .filter(s -> eventType.equals(s.getEventType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported event type: " + eventType));
    }

}
