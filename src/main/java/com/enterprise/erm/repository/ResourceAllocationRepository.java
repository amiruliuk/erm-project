package com.enterprise.erm.repository;
import com.enterprise.erm.domain.ResourceAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ResourceAllocationRepository extends JpaRepository<ResourceAllocation, Long> {
    @Override
    @EntityGraph(attributePaths = {"employee", "project"})
    List<ResourceAllocation> findAll();

    @EntityGraph(attributePaths = {"employee", "project"})
    List<ResourceAllocation> findByEmployeeId(Long employeeId);

    @EntityGraph(attributePaths = {"employee", "project"})
    List<ResourceAllocation> findByEmployeeUnitId(Long unitId);
}
