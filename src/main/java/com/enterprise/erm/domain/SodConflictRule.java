package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sod_conflict_rule")
@Getter @Setter
public class SodConflictRule extends BaseEntity {
    @Column(name = "role_a", nullable = false, length = 80)
    private String roleA;

    @Column(name = "role_b", nullable = false, length = 80)
    private String roleB;
    @Column(nullable = false) private boolean conflictFlag = true;
    @Column(length = 500)     private String description;
}