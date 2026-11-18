package com.enterprise.erm.domain.dto;


import java.util.List;

public record SkillMatrixRow(
        Long skillId,
        String skillName,
        boolean critical,
        long peopleAtLevel3Plus,
        boolean singlePersonRisk,
        List<SkillMatrixGroup> groups) {}    // <- was List<SkillMatrixCell> cells