package com.enterprise.erm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "backup_roster",
        uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id"}))
@Getter @Setter
public class BackupRoster extends BaseEntity {

    /** The critical activity — modelled as the critical Skill it depends on. One roster row per activity. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Skill activity;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Employee primaryPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    private Employee backup1;

    @ManyToOne(fetch = FetchType.LAZY)
    private Employee backup2;

    /** Latest readiness test — denormalised pointer for the fast roster view (kept in sync by the controller). Its evidence is reached via lastTest.evidence. */
    @ManyToOne(fetch = FetchType.LAZY)
    private BackupReadinessTest lastTest;
}
