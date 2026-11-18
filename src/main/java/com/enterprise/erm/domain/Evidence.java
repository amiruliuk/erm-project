package com.enterprise.erm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "evidence",
        indexes = @Index(name = "idx_evidence_status", columnList = "verificationStatus"))
@Getter @Setter
public class Evidence extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EvidenceType type;

    /** File lives in storage (same convention as photos/credentials). */
    private String fileName;
    private String originalName;
    private String contentType;
    private Long size;

    private LocalDate recordedOn;

    @Column(nullable = false, length = 100)
    private String recordedBy;      // username

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    private Employee verifiedBy;

    private LocalDate verifiedOn;

    @Column(length = 1000)
    private String verificationNotes;

    public enum EvidenceType {
        RUNBOOK, TRAINING_ATTENDANCE, READINESS_TEST_RESULT, REVIEW_MINUTES, OTHER
    }
}