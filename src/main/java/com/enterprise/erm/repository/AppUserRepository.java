package com.enterprise.erm.repository;
import com.enterprise.erm.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AppUserRepository extends JpaRepository<AppUser,Long> {
 Optional<AppUser> findByUsername(String username);
 boolean existsByUsername(String username);
 List<AppUser> findAllByOrderByUsernameAsc();
 long countByRoleAndEnabledTrue(String role);






}
