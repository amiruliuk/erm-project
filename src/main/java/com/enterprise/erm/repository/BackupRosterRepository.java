package com.enterprise.erm.repository;

import com.enterprise.erm.domain.BackupRoster;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupRosterRepository extends JpaRepository<BackupRoster, Long> {

    Optional<BackupRoster> findByActivityId(Long activityId);

    /** lastTest.evidence / lastTest.performedBy are read by the roster page, so they are fetched here too. */
    @EntityGraph(attributePaths = {"activity", "primaryPerson", "backup1", "backup2",
            "lastTest", "lastTest.evidence", "lastTest.performedBy"})
    List<BackupRoster> findByActivityCriticalTrue();

    /** Roster with every association the form/history pages need (open-in-view is off). */
    @EntityGraph(attributePaths = {"activity", "primaryPerson", "backup1", "backup2"})
    Optional<BackupRoster> findWithDetailsById(Long id);
}
