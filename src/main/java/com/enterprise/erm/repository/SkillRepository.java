package com.enterprise.erm.repository;

import com.enterprise.erm.domain.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    List<Skill> findByCriticalTrue();

    Optional<Skill> findByName(String name);

    List<Skill> findByCategory(String category);
}