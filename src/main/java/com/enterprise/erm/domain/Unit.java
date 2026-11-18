package com.enterprise.erm.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * A unit (section / desk / team) that belongs to exactly one department.
 * Employees and projects are attached to a unit, so people, work, allocation, RACI,
 * backup roster, training plans and credentials can all be managed and reported unit-wise.
 */
@Entity
@Table(name = "units",
        uniqueConstraints = @UniqueConstraint(name = "uk_units_dept_name", columnNames = {"department_id", "name"}))
@Getter @Setter
public class Unit extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    /** Optional short code; unique across all units when present. */
    @Column(unique = true)
    private String code;

    @Column(length = 1000)
    private String description;

    private boolean active = true;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /** Unit head / team lead (must be an employee of the same department). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_head_id")
    private Employee unitHead;
}
