package com.enterprise.erm.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "employees")
@Getter @Setter
public class Employee extends BaseEntity {

    @Column(nullable = false, unique = true) private String employeeCode;
    @Column(nullable = false) private String fullName;
    @Column(nullable = false, unique = true) private String email;
    private String jobTitle;
    private String phone;
    private String employmentStatus = "ACTIVE";

    // Image metadata (file itself lives in storage)
    private String photoFileName;     // generated name, e.g. UUID.jpg
    private String photoContentType;  // image/jpeg, image/png

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    /** Unit inside the department. Must belong to the same department (enforced in the controller/service). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Unit unit;
}