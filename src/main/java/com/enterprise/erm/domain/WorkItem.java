package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="work_items") @Getter @Setter
public class WorkItem extends BaseEntity {
 @Column(nullable=false) private String title;
 @Column(length=3000) private String description;
 private String status="OPEN"; private String priority="MEDIUM";
 private LocalDate dueDate; private Integer estimatedHours;
 @ManyToOne(optional=false) private Project project;
 @ManyToOne private Employee assignee;
 @ManyToOne private Employee createdBy;
}