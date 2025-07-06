package thong.test.customerpointservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.service.modify_point_strategy.ModifyPointStrategyProvider;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModifyPointKafkaConsumer {

    private final ModifyPointStrategyProvider modifyPointStrategyProvider;

    @KafkaListener(
            topics = "${service.kafka.modify-point-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "modifyPointEventRecordConsumerFactory"
    )
    public void consume(ModifyPointEventRecord<?> record, Acknowledgment ack) {

        if (record == null) {
            log.warn("Received null record, skip");
            return;
        }

        var castedRecord = (ModifyPointEventRecord) record;
        try {
            var strategy = modifyPointStrategyProvider.getStrategy(record.getModifyPointEventType());
            strategy.doModifyPoint(castedRecord);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to consume point earned event", e);
        }
    }

}
