package com.enterprise.erm.repository;
import com.enterprise.erm.domain.WorkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkItemRepository extends JpaRepository<WorkItem,Long> {
 List<WorkItem> findByProjectId(Long projectId);
 List<WorkItem> findByAssigneeId(Long assigneeId);
 long countByStatusNot(String status);

 /** tasks whose assignee belongs to the unit, or whose project is owned by the unit */
 @org.springframework.data.jpa.repository.Query("select distinct w from WorkItem w left join w.assignee a left join w.project p "
         + "where a.unit.id = :unitId or p.owningUnit.id = :unitId")
 List<WorkItem> findForUnit(@org.springframework.data.repository.query.Param("unitId") Long unitId);
}
