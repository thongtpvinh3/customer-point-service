package thong.test.customerpointservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import thong.test.customerpointservice.entities.PointEntity;

import java.util.List;

@Repository
public interface PointRepository extends JpaRepository<PointEntity, Long> {

    PointEntity findByUserId(long userId);

    @Query("SELECT u FROM PointEntity u WHERE u.userId IN :ids")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PointEntity> findByIdsForUpdate(@Param("ids") List<Long> ids);
}
