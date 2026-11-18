package com.enterprise.erm.repository;

import com.enterprise.erm.domain.EmployeeCredential;
import com.enterprise.erm.domain.VerificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeCredentialRepository extends JpaRepository<EmployeeCredential, Long> {
    @Override
    @EntityGraph(attributePaths = {"employee", "skill", "verifiedBy"})
    List<EmployeeCredential> findAll();

    @EntityGraph(attributePaths = {"employee", "skill", "verifiedBy"})
    List<EmployeeCredential> findByEmployeeIdOrderByIssueDateDesc(Long employeeId);

    @EntityGraph(attributePaths = {"employee", "skill", "verifiedBy"})
    List<EmployeeCredential> findByVerificationStatus(VerificationStatus status);

    @EntityGraph(attributePaths = {"employee", "skill"})
    List<EmployeeCredential> findByExpiryDateBetween(LocalDate from, LocalDate to);

    /** employee is LAZY and open-in-view is off, so controllers needing c.getEmployee() must use this finder. */
    @EntityGraph(attributePaths = {"employee", "skill", "verifiedBy"})
    java.util.Optional<EmployeeCredential> findWithEmployeeById(Long id);

    long countByVerificationStatus(VerificationStatus status);


    @EntityGraph(attributePaths = {"employee", "skill", "verifiedBy"})
    List<EmployeeCredential> findByEmployeeUnitId(Long unitId);
}
