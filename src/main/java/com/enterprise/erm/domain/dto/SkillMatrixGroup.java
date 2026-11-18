package com.enterprise.erm.domain.dto;

import java.util.List;

public record SkillMatrixGroup(
        Long departmentId,
        String departmentName,
        List<String> employeeCodes,          // column headers within the group
        List<SkillMatrixCell> cellsForGroup  // one per employee, same order as employeeCodes
) {}
