package thong.test.customerpointservice.service.modify_point_strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.pojo.dto.TransferPointMetaData;
import thong.test.customerpointservice.service.UserPointService;
import thong.test.customerpointservice.service.modify_point_strategy.ModifyPointStrategy;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferFromAnotherUserStrategy implements ModifyPointStrategy<TransferPointMetaData> {

    private final UserPointService userPointService;

    @Override
    public int getPoint(ModifyPointEventRecord<TransferPointMetaData> event) {
        if (event.getUserId() == null || event.getUserId() <= 0) {
            return 0;
        }
        var metaData = event.getEventMetaData();
        if (metaData == null) {
            return 0;
        }
        return metaData.getTransferredAmount() == null ? 0 : metaData.getTransferredAmount();
    }

    @Override
    public void doModifyPoint(ModifyPointEventRecord<TransferPointMetaData> event) {
        userPointService.transferPoint(event);
    }

    @Override
    public ModifyPointTypeEnum getEventType() {
        return ModifyPointTypeEnum.TRANSFER_FROM_ANOTHER_USER;
    }

}
