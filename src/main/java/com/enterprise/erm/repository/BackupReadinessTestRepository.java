package com.enterprise.erm.repository;

import com.enterprise.erm.domain.BackupReadinessTest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupReadinessTestRepository extends JpaRepository<BackupReadinessTest, Long> {
    Optional<BackupReadinessTest> findTopByRosterIdOrderByTestedOnDesc(Long rosterId);

    /** Everything the history page shows is fetched here (open-in-view is off). */
    @EntityGraph(attributePaths = {"performedBy", "evidence"})
    List<BackupReadinessTest> findByRosterIdOrderByTestedOnDesc(Long rosterId);
}
