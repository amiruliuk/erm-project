package com.enterprise.erm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employee_credentials", indexes = {
        @Index(name = "idx_cred_employee", columnList = "employee_id"),
        @Index(name = "idx_cred_expiry", columnList = "expiryDate"),
        @Index(name = "idx_cred_status", columnList = "verificationStatus")
})
@Getter @Setter
public class EmployeeCredential extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CredentialType type;

    @NotBlank @Column(nullable = false)
    private String title;                    // "BSc Computer Science", "PMP", "Advanced Java"

    @NotBlank @Column(nullable = false)
    private String issuer;                   // university, PMI, Coursera, internal L&D

    private String credentialNumber;         // certificate / licence ID
    private String credentialUrl;            // online verification link

    // Academic-specific
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EducationLevel educationLevel;
    private String fieldOfStudy;
    private String grade;                    // "First Class", "3.8 GPA", "Pass"

    // Training-specific
    private Integer durationHours;
    @Column(precision = 6, scale = 2)
    private BigDecimal cpdPoints;            // continuing professional development credits

    // Validity
    private LocalDate issueDate;
    private LocalDate expiryDate;            // null = never expires

    // Optional link: this credential is evidence for a skill
    @ManyToOne(fetch = FetchType.LAZY)
    private Skill skill;

    // Attached document (file lives in storage, as with photos)
    private String documentFileName;
    private String documentOriginalName;     // for display / download name only
    private String documentContentType;
    private Long documentSize;

    // Verification workflow
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee verifiedBy;
    private LocalDate verifiedOn;
    @Column(length = 1000)
    private String verificationNotes;

    // Convenience for Thymeleaf: ${c.expired}, ${c.expiringSoon}
    @Transient
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    @Transient
    public boolean isExpiringSoon() {
        return expiryDate != null && !isExpired()
                && !expiryDate.isAfter(LocalDate.now().plusDays(60));
    }
}