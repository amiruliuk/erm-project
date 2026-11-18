package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="audit_logs") @Getter @Setter
public class AuditLog extends BaseEntity {
 private String username; private String action; private String entityType; private Long entityId;
 @Column(length=3000) private String details; private String ipAddress;
}