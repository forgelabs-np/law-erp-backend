//package com.lawfirm.erp.firm.service;
//
//import com.lawfirm.erp.common.exception.BusinessRuleException;
//import com.lawfirm.erp.common.exception.DuplicateResourceException;
//import com.lawfirm.erp.common.exception.ForbiddenException;
//import com.lawfirm.erp.common.exception.ResourceNotFoundException;
//import com.lawfirm.erp.dto.firm.request.DepartmentRequest;
//import com.lawfirm.erp.dto.firm.response.DepartmentResponse;
//import com.lawfirm.erp.firm.entity.Department;
//import com.lawfirm.erp.firm.entity.Firm;
//import com.lawfirm.erp.firm.repository.DepartmentRepository;
//import com.lawfirm.erp.firm.repository.FirmRepository;
//import com.lawfirm.erp.security.CurrentUserResolver;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.UUID;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class DepartmentService {
//
//    private final DepartmentRepository departmentRepository;
//    private final FirmRepository firmRepository;
//    private final CurrentUserResolver currentUserResolver;
//
//    @Transactional
//    public DepartmentResponse createDepartment(DepartmentRequest request) {
//        UUID firmId = getCurrentFirmId();
//        Firm firm = firmRepository.findById(firmId)
//                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));
//
//        // Check unique code within firm
//        if (departmentRepository.existsByFirmIdAndCode(firmId, request.getCode())) {
//            throw new DuplicateResourceException("Department code already exists in your firm");
//        }
//
//        Department department = Department.builder()
//                .firm(firm)
//                .name(request.getName())
//                .code(request.getCode().toUpperCase())
//                .description(request.getDescription())
//                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
//                .build();
//        department.setActive(request.getIsActive() != null ? request.getIsActive() : true);
//
//        department = departmentRepository.save(department);
//        log.info("Department created: {} in firm {}", department.getCode(), firm.getLawFirmCode());
//
//        return toResponse(department);
//    }
//
//    public List<DepartmentResponse> getAllDepartments() {
//        UUID firmId = getCurrentFirmId();
//        return departmentRepository.findByFirmIdOrderByDisplayOrderAsc(firmId)
//                .stream()
//                .map(this::toResponse)
//                .collect(Collectors.toList());
//    }
//
//    public DepartmentResponse getDepartmentById(UUID departmentId) {
//        UUID firmId = getCurrentFirmId();
//        Department department = getDepartmentValidated(departmentId, firmId);
//        return toResponse(department);
//    }
//
//    @Transactional
//    public DepartmentResponse updateDepartment(UUID departmentId, DepartmentRequest request) {
//        UUID firmId = getCurrentFirmId();
//        Department department = getDepartmentValidated(departmentId, firmId);
//
//        if (request.getName() != null) department.setName(request.getName());
//        if (request.getDescription() != null) department.setDescription(request.getDescription());
//        if (request.getDisplayOrder() != null) department.setDisplayOrder(request.getDisplayOrder());
//        if (request.getIsActive() != null) department.setActive(request.getIsActive());
//
//        // Check code uniqueness if changed
//        if (request.getCode() != null && !request.getCode().equals(department.getCode())) {
//            if (departmentRepository.existsByFirmIdAndCode(firmId, request.getCode())) {
//                throw new DuplicateResourceException("Department code already exists");
//            }
//            department.setCode(request.getCode().toUpperCase());
//        }
//
//        department = departmentRepository.save(department);
//        log.info("Department updated: {} in firm {}", department.getCode(), firmId);
//
//        return toResponse(department);
//    }
//
//    @Transactional
//    public void deleteDepartment(UUID departmentId) {
//        UUID firmId = getCurrentFirmId();
//        Department department = getDepartmentValidated(departmentId, firmId);
//
//        // Check if department has employees
//        long employeeCount = departmentRepository.countEmployeesByDepartmentId(departmentId);
//        if (employeeCount > 0) {
//            throw new BusinessRuleException(
//                    "Cannot delete department with " + employeeCount + " employees. Reassign employees first."
//            );
//        }
//
//        departmentRepository.delete(department);
//        log.info("Department deleted: {} from firm {}", department.getCode(), firmId);
//    }
//
//    private UUID getCurrentFirmId() {
//        UUID firmId = currentUserResolver.getCurrentFirmId();
//        if (firmId == null) {
//            throw new ForbiddenException("This action requires a firm context");
//        }
//        return firmId;
//    }
//
//    private Department getDepartmentValidated(UUID departmentId, UUID firmId) {
//        Department department = departmentRepository.findById(departmentId)
//                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
//
//        if (!department.getFirm().getId().equals(firmId)) {
//            throw new ForbiddenException("Department does not belong to your firm");
//        }
//        return department;
//    }
//
//    private DepartmentResponse toResponse(Department department) {
//        return DepartmentResponse.builder()
//                .id(department.getId())
//                .name(department.getName())
//                .code(department.getCode())
//                .description(department.getDescription())
//                .displayOrder(department.getDisplayOrder())
//                .isActive(department.isActive())
//                .createdAt(department.getCreatedAt())
//                .build();
//    }
//}