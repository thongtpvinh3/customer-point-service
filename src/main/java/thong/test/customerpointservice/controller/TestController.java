package thong.test.customerpointservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;
import thong.test.customerpointservice.pojo.dto.CreateNewUserMetaData;
import thong.test.customerpointservice.pojo.dto.TransferPointMetaData;
import thong.test.customerpointservice.service.modify_point_strategy.ModifyPointStrategyProvider;
import thong.test.customerpointservice.service.modify_point_strategy.impl.CreateNewStrategy;
import thong.test.customerpointservice.service.modify_point_strategy.impl.TransferFromAnotherUserStrategy;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class TestController {

    private final ModifyPointStrategyProvider  modifyPointStrategyProvider;

    private final CreateNewStrategy createNewStrategy;

    private final TransferFromAnotherUserStrategy  transferFromAnotherUserStrategy;

    @PostMapping("/add")
    public ResponseEntity<Void> testAdd(@RequestBody ModifyPointEventRecord<CreateNewUserMetaData> event) {
        if (event == null) {
            log.warn("Received null record, skip");
            return ResponseEntity.badRequest().build();
        }

        try {
            createNewStrategy.doModifyPoint(event);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to consume point earned event", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> testTransfer(@RequestBody ModifyPointEventRecord<TransferPointMetaData> event) {
        if (event == null) {
            log.warn("Received null record, skip");
            return ResponseEntity.badRequest().build();
        }

        try {
            transferFromAnotherUserStrategy.doModifyPoint(event);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to consume point earned event", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
