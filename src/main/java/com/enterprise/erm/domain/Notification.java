package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="notifications") @Getter @Setter
public class Notification extends BaseEntity {
 @ManyToOne(optional=false) private Employee recipient;
 @Column(nullable=false,length=1000) private String message;
 private String category; private boolean readFlag=false;
}