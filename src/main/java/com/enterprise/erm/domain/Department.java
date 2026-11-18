package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="departments") @Getter @Setter
public class Department extends BaseEntity {
 @NotBlank @Column(nullable=false, unique=true) private String name;
 private String code; private String description; private boolean active=true;
}