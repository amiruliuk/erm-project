package com.enterprise.erm.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="skills") @Getter @Setter
public class Skill extends BaseEntity {
 @Column(nullable=false,unique=true) private String name;
 private String category; private String description;
    // Skill.java — add one field
    private boolean critical = false;
}