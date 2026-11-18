package com.enterprise.erm.service;

import com.enterprise.erm.domain.Department;
import com.enterprise.erm.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Department findById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + id));
    }

    public Department create(Department department) {
        validateUnique(department, null);
        department.setId(null);
        return departmentRepository.save(department);
    }

    public Department update(Long id, Department department) {
        Department existing = findById(id);
        validateUnique(department, id);
        existing.setName(department.getName());
        existing.setCode(department.getCode());
        existing.setDescription(department.getDescription());
        existing.setActive(department.isActive());
        return departmentRepository.save(existing);
    }

    public void delete(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new IllegalArgumentException("Department not found: " + id);
        }
        departmentRepository.deleteById(id);
    }

    private void validateUnique(Department department, Long id) {
        boolean nameExists = (id == null)
                ? departmentRepository.existsByName(department.getName())
                : departmentRepository.existsByNameAndIdNot(department.getName(), id);
        if (nameExists) {
            throw new IllegalArgumentException("Department name already exists: " + department.getName());
        }

        if (department.getCode() != null && !department.getCode().isBlank()) {
            boolean codeExists = (id == null)
                    ? departmentRepository.existsByCode(department.getCode())
                    : departmentRepository.existsByCodeAndIdNot(department.getCode(), id);
            if (codeExists) {
                throw new IllegalArgumentException("Department code already exists: " + department.getCode());
            }
        }
    }
}