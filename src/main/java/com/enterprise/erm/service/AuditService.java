package com.enterprise.erm.service;
import com.enterprise.erm.domain.AuditLog;
import com.enterprise.erm.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class AuditService {
 private final AuditLogRepository logs;
 public void record(String username,String action,String type,Long id,String details,String ip){
  AuditLog a=new AuditLog();a.setUsername(username);a.setAction(action);a.setEntityType(type);a.setEntityId(id);a.setDetails(details);a.setIpAddress(ip);logs.save(a);
 }
}
