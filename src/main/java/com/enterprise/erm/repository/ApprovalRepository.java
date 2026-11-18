package com.enterprise.erm.repository;
import com.enterprise.erm.domain.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ApprovalRepository extends JpaRepository<Approval,Long> {






 long countByStatus(String status);
}
