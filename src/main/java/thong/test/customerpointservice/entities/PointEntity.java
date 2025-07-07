package thong.test.customerpointservice.entities;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "user_bonus_point")
public class PointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id",  nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, name = "current_point")
    private Long currentPoint;

}

