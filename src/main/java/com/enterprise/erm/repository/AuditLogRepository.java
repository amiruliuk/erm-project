package com.enterprise.erm.repository;
import com.enterprise.erm.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AuditLogRepository extends JpaRepository<AuditLog,Long> {
 List<AuditLog> findTop200ByOrderByCreatedAtDesc();
}
