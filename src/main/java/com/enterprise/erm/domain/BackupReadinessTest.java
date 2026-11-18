package com.enterprise.erm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "backup_readiness_test",
        indexes = @Index(name = "idx_brt_roster", columnList = "roster_id"))
@Getter @Setter
public class BackupReadinessTest extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private BackupRoster roster;

    /** The backup person who performed the activity with the primary absent/observing. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Employee performedBy;

    private LocalDate testedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Result result = Result.PARTIAL;

    @Column(length = 2000)
    private String notes;

    /** Single evidence model: the uploaded result lives in the Evidence register (v2 also had four duplicate file columns here). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Evidence evidence;

    public enum Result { PASS, PARTIAL, FAIL }
}
