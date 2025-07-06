package thong.test.customerpointservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thong.test.customerpointservice.entities.TransactionLogEntity;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLogEntity, String> {

    boolean existsByTransactionId(String transactionId);

}

