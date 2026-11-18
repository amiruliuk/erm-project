package com.enterprise.erm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;

@Entity
@Table(name = "employee_skills",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "skill_id"}))
@Getter @Setter
public class EmployeeSkill extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Employee employee;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Skill skill;

    @Min(1) @Max(5)
    @Column(nullable = false)
    private int proficiency = 1; // 1 to 5 scale matching CBS-GOV-003

    private BigDecimal yearsOfExperience;
    private LocalDate lastUsedOn;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssessmentSource source = AssessmentSource.SELF;   // SELF, MANAGER, CERTIFICATION, TEST

    @Column(length = 2000)
    private String evidence;

    private LocalDate assessedOn;

    public enum AssessmentSource { SELF, MANAGER, CERTIFICATION, TEST }
}