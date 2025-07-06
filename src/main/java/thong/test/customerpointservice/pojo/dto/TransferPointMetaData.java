package thong.test.customerpointservice.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferPointMetaData implements ModifyPointMetaData {

    private Long receiverUserId;

    private Integer transferredAmount;

    private String note;

    private String refId;
}