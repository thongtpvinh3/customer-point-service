package thong.test.customerpointservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import thong.test.customerpointservice.entities.PointEventConfigEntity;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;
import thong.test.customerpointservice.repository.PointEventConfigRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PointEventConfigService {

    private final PointEventConfigRepository configRepository;


    public Optional<PointEventConfigEntity> getConfig(ModifyPointTypeEnum eventType) {

        List<PointEventConfigEntity> cachedConfigs = configRepository.findValidConfigs(eventType, LocalDateTime.now());

        return cachedConfigs.stream()
                .filter(c -> eventType.equals(c.getEventType()))
                .findFirst();
    }
}
