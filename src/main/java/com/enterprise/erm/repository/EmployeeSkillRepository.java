package com.enterprise.erm.repository;
import com.enterprise.erm.domain.EmployeeSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill,Long> {

    @Override
    @EntityGraph(attributePaths = {"employee", "skill"})
    List<EmployeeSkill> findAll();

    @EntityGraph(attributePaths = {"employee", "skill"})
    List<EmployeeSkill> findByEmployeeId(Long employeeId);

 Optional<EmployeeSkill> findByEmployeeIdAndSkillId(Long employeeId, Long skillId);
}
