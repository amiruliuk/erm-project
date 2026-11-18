package com.enterprise.erm.domain;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "privileged_role_register")
public class PrivilegedRoleRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private String staffId;

    @Column(name = "staff_name", nullable = false)
    private String staffName;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Column(name = "granted_on", nullable = false)
    private LocalDate grantedOn;

    @Column(name = "approved_by", nullable = false)
    private String approvedBy;

    @Column(name = "last_reviewed", nullable = false)
    private LocalDate lastReviewed;

    @Column(name = "exception_approved")
    private Boolean exceptionApproved = false;

    @Column(name = "compensating_control", columnDefinition = "TEXT")
    private String compensatingControl;

    @Column(name = "exception_expiry")
    private LocalDate exceptionExpiry;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStaffId() { return staffId; }
    public void setStaffId(String staffId) { this.staffId = staffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }

    public LocalDate getGrantedOn() { return grantedOn; }
    public void setGrantedOn(LocalDate grantedOn) { this.grantedOn = grantedOn; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDate getLastReviewed() { return lastReviewed; }
    public void setLastReviewed(LocalDate lastReviewed) { this.lastReviewed = lastReviewed; }

    public Boolean getExceptionApproved() { return exceptionApproved; }
    public void setExceptionApproved(Boolean exceptionApproved) { this.exceptionApproved = exceptionApproved; }

    public String getCompensatingControl() { return compensatingControl; }
    public void setCompensatingControl(String compensatingControl) { this.compensatingControl = compensatingControl; }

    public LocalDate getExceptionExpiry() { return exceptionExpiry; }
    public void setExceptionExpiry(LocalDate exceptionExpiry) { this.exceptionExpiry = exceptionExpiry; }
}