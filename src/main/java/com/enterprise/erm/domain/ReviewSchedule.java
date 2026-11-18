package com.enterprise.erm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "review_schedule")
@Getter @Setter
public class ReviewSchedule extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String scope;              // "CBS-GOV-003"

    private LocalDate dueOn;
    private LocalDate completedOn;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Outcome outcome;           // null = not completed

    @Column(length = 2000)
    private String notes;
    @ManyToOne(fetch = FetchType.LAZY)
    private Evidence evidence;

    // ReviewSchedule.java
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Employee owner;

    public enum Outcome { PASS, PARTIAL, FAIL }
}