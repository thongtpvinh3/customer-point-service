package thong.test.customerpointservice.entities;

import jakarta.persistence.*;
import lombok.Data;
import thong.test.customerpointservice.enums.ModifyPointTypeEnum;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "point_event_config")
public class PointEventConfigEntity {

    @Id
    private Integer id;

    @Column(name = "event_type")
    @Enumerated(EnumType.STRING)
    private ModifyPointTypeEnum eventType;

    @Column(name = "event_source")
    private String eventSource;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "point_value")
    private Integer pointValue;

    @Column(name = "condition_expr")
    private String conditionExpr;

    @Column(name = "applicable_from")
    private LocalDateTime applicableFrom;

    @Column(name = "applicable_to")
    private LocalDateTime applicableTo;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "last_modified")
    private LocalDateTime lastModified;
}
