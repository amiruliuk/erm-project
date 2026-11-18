package com.enterprise.erm.repository;

import com.enterprise.erm.domain.Unit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    /** department and unitHead are LAZY and open-in-view is off, so fetch them for every list/detail page. */
    @Override
    @EntityGraph(attributePaths = {"department", "unitHead"})
    List<Unit> findAll();

    @EntityGraph(attributePaths = {"department", "unitHead"})
    Optional<Unit> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"department", "unitHead"})
    List<Unit> findByDepartmentIdOrderByName(Long departmentId);

    @EntityGraph(attributePaths = {"department", "unitHead"})
    List<Unit> findByActiveTrueOrderByName();

    boolean existsByDepartmentIdAndNameIgnoreCase(Long departmentId, String name);
    boolean existsByDepartmentIdAndNameIgnoreCaseAndIdNot(Long departmentId, String name, Long id);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    long countByDepartmentId(Long departmentId);
    List<Unit> findByUnitHeadId(Long employeeId);
}
