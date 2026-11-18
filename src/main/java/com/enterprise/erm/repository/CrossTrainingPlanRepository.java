package com.enterprise.erm.repository;

import com.enterprise.erm.domain.CrossTrainingPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CrossTrainingPlanRepository extends JpaRepository<CrossTrainingPlan, Long> {
    List<CrossTrainingPlan> findByStatus(CrossTrainingPlan.PlanStatus status);
    List<CrossTrainingPlan> findByDependencyId(Long dependencyId);

    /** Plans still open whose target date has passed (used by the daily scheduler). */
    @EntityGraph(attributePaths = {"dependency", "owner"})
    List<CrossTrainingPlan> findByStatusInAndTargetDateBefore(Collection<CrossTrainingPlan.PlanStatus> statuses, LocalDate date);

    @Override
    @EntityGraph(attributePaths = {"dependency", "owner"})
    List<CrossTrainingPlan> findAll();
}
