package com.enterprise.erm.repository;

import com.enterprise.erm.domain.ReviewSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {
    List<ReviewSchedule> findByCompletedOnIsNullAndDueOnBefore(LocalDate date);
    Optional<ReviewSchedule> findTopByScopeOrderByDueOnDesc(String scope);

    // ReviewScheduleRepository.java
    @EntityGraph(attributePaths = {"owner"})
    List<ReviewSchedule> findAll();

    @EntityGraph(attributePaths = {"owner"})
    Optional<ReviewSchedule> findById(Long id);

}