package com.enterprise.erm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "cross_training_plan")
@Getter @Setter
public class CrossTrainingPlan extends BaseEntity {

    /** The key-person dependency this plan mitigates (a critical Skill). */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Skill dependency;

    @Column(nullable = false, length = 2000)
    private String action;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Employee owner;

    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.PLANNED;

    @Column(length = 2000)
    private String notes;
    @ManyToOne(fetch = FetchType.LAZY)
    private Evidence evidence;
    public enum PlanStatus { PLANNED, IN_PROGRESS, DONE, OVERDUE }
}