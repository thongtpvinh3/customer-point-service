package thong.test.customerpointservice.service.modify_point_strategy;

import thong.test.customerpointservice.entities.PointEventConfigEntity;
import thong.test.customerpointservice.entities.UserBonusPointEntity;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.pojo.dto.ModifyPointMetaData;
import thong.test.customerpointservice.service.PointEventConfigService;

import java.util.function.Consumer;

public interface ModifyPointStrategy<T extends ModifyPointMetaData> {

    int getPoint(ModifyPointEventRecord<T> event);

    void doModifyPoint(ModifyPointEventRecord<T> event);

    ModifyPointTypeEnum getEventType();

    default PointEventConfigEntity getEventConfig(PointEventConfigService service, ModifyPointTypeEnum eventType) {
        var configs = service.getConfig(eventType);
        return configs.stream()
                .filter(c -> eventType.equals(c.getEventType()))
                .findFirst()
                .orElse(null);
    }
}
