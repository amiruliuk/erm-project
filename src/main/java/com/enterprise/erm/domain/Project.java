package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="projects") @Getter @Setter
public class Project extends BaseEntity {
 @Column(nullable=false,unique=true) private String projectCode;
 @Column(nullable=false) private String name;
 @Column(length=2000) private String description;
 private String status="PLANNING"; private String priority="MEDIUM";
 private LocalDate startDate; private LocalDate endDate;
 @ManyToOne(fetch=FetchType.LAZY) private Department owningDepartment;
 @ManyToOne(fetch=FetchType.LAZY) private Unit owningUnit;
 @ManyToOne(fetch=FetchType.LAZY) private Employee projectManager;
}