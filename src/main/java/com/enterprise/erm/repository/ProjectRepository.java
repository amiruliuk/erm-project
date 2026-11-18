package com.enterprise.erm.repository;
import com.enterprise.erm.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
public interface ProjectRepository extends JpaRepository<Project,Long> {
 @Override @EntityGraph(attributePaths = {"owningDepartment","owningUnit","projectManager"})
 List<Project> findAll();

 @EntityGraph(attributePaths = {"owningDepartment","owningUnit","projectManager"})
 List<Project> findByOwningUnitId(Long unitId);

 long countByOwningUnitId(Long unitId);







}
