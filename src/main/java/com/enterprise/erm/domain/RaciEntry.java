package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="raci_entries") @Getter @Setter
public class RaciEntry extends BaseEntity {
 @ManyToOne(optional=false) private Project project;
 @Column(nullable=false) private String activity;
 @ManyToOne(optional=false) private Employee employee;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private RaciRole role;
 public enum RaciRole { RESPONSIBLE, ACCOUNTABLE, CONSULTED, INFORMED }
}