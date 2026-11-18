package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="resource_allocations") @Getter @Setter
public class ResourceAllocation extends BaseEntity {
 @ManyToOne(optional=false) private Employee employee;
 @ManyToOne(optional=false) private Project project;
 private LocalDate startDate; private LocalDate endDate;
 private BigDecimal allocationPercent;
 private String notes;
}