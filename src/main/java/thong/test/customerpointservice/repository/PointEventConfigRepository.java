package thong.test.customerpointservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import thong.test.customerpointservice.entities.PointEventConfigEntity;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PointEventConfigRepository extends JpaRepository<PointEventConfigEntity, Integer> {

    @Query("SELECT c FROM PointEventConfigEntity c " +
            "WHERE c.eventType = :eventType AND c.isActive = true " +
            "AND (c.applicableFrom IS NULL OR c.applicableFrom <= :now) " +
            "AND (c.applicableTo IS NULL OR c.applicableTo >= :now)")
    List<PointEventConfigEntity> findValidConfigs(@Param("eventType") ModifyPointTypeEnum eventType,
                                                  @Param("now") LocalDateTime now);
}
