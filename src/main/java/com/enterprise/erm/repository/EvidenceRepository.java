package com.enterprise.erm.repository;

import com.enterprise.erm.domain.Evidence;
import com.enterprise.erm.domain.VerificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    @Override
    @EntityGraph(attributePaths = {"verifiedBy"})
    List<Evidence> findAll();

    @EntityGraph(attributePaths = {"verifiedBy"})
    List<Evidence> findByVerificationStatus(VerificationStatus status);
}
