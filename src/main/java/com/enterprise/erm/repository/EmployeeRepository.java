package com.enterprise.erm.repository;
import com.enterprise.erm.domain.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface EmployeeRepository extends JpaRepository<Employee,Long> {

 Optional<Employee> findByEmail(String email);

 Optional<Employee> findByEmployeeCode(String employeeCode);

 /** department is LAZY and open-in-view is off, so fetch it here or the templates throw LazyInitializationException */
 @Override @EntityGraph(attributePaths = {"department", "unit"})
 List<Employee> findAll();

 @EntityGraph(attributePaths = {"department", "unit"})
 List<Employee> findByUnitId(Long unitId);

 @EntityGraph(attributePaths = {"department", "unit"})
 List<Employee> findByDepartmentId(Long departmentId);

 long countByUnitId(Long unitId);
 long countByDepartmentId(Long departmentId);

 /** Row lock so two simultaneous allocations for the same employee are serialised. */
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select e from Employee e where e.id = :id")
 Optional<Employee> lockById(@Param("id") Long id);
}
