package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="approvals") @Getter @Setter
public class Approval extends BaseEntity {
 @ManyToOne(optional=false) private Project project;
 @ManyToOne private WorkItem workItem;
 @ManyToOne(optional=false) private Employee requestedBy;
 @ManyToOne private Employee approver;
 private String approvalType; private String status="PENDING";
 @Column(length=2000) private String comments;
}