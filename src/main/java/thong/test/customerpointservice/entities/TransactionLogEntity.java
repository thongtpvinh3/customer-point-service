package thong.test.customerpointservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "point_transaction_log")
public class TransactionLogEntity {

    @Id
    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    private Integer amount;

    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
