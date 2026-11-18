package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="app_users") @Getter @Setter
public class AppUser extends BaseEntity {
 @Column(nullable=false,unique=true) private String username;
 @Column(nullable=false) private String password;
 @Column(nullable=false) private String role="ROLE_USER";
 private boolean enabled=true;
 /** Links this login to an employee record (needed for maker-checker, "my work", etc.). */
 private String employeeCode;
}