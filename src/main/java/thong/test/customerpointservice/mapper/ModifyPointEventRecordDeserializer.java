package thong.test.customerpointservice.mapper;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.stereotype.Component;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.pojo.dto.CreateNewUserMetaData;
import thong.test.customerpointservice.pojo.dto.ModifyPointMetaData;
import thong.test.customerpointservice.pojo.dto.TransferPointMetaData;

@Component
@Slf4j
public class ModifyPointEventRecordDeserializer implements Deserializer<ModifyPointEventRecord<?>> {

    @Override
    public ModifyPointEventRecord<?> deserialize(String s, byte[] data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(data);
            ModifyPointTypeEnum type = ModifyPointTypeEnum.valueOf(root.get("modifyPointEventType").asText());

            Class<? extends ModifyPointMetaData> metaClass = switch (type) {
                case CREATE_NEW -> CreateNewUserMetaData.class;
                case TRANSFER_FROM_ANOTHER_USER -> TransferPointMetaData.class;
                default -> throw new IllegalArgumentException("Unsupported event type: " + type);
            };

            JavaType fullType = mapper.getTypeFactory()
                    .constructParametricType(ModifyPointEventRecord.class, metaClass);

            return mapper.readValue(data, fullType);
        } catch (Exception e) {
            log.error("Failed to deserialize ModifyPointEventRecord", e);
            return null;
        }
    }
}
