package thong.test.customerpointservice.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;
import thong.test.customerpointservice.pojo.dto.ModifyPointMetaData;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifyPointEventRecord<T extends ModifyPointMetaData> {

    private String transactionId;

    private Long userId;

    private ModifyPointTypeEnum modifyPointEventType;

    private String source;

    private T eventMetaData;

    private LocalDateTime timestamp;

}
