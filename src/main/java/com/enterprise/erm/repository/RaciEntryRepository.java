package com.enterprise.erm.repository;
import com.enterprise.erm.domain.RaciEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RaciEntryRepository extends JpaRepository<RaciEntry,Long> {
 List<RaciEntry> findByProjectId(Long projectId);
 List<RaciEntry> findByEmployeeId(Long employeeId);
 boolean existsByProjectIdAndActivityIgnoreCaseAndRole(Long projectId, String activity, RaciEntry.RaciRole role);
 boolean existsByProjectIdAndActivityIgnoreCaseAndEmployeeId(Long projectId, String activity, Long employeeId);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"project", "employee"})
 List<RaciEntry> findByEmployeeUnitId(Long unitId);
}
