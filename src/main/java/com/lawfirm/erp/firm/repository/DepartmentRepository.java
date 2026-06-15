//package com.lawfirm.erp.firm.repository;
//
//import com.lawfirm.erp.firm.entity.Department;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//@Repository
//public interface DepartmentRepository extends JpaRepository<Department, UUID> {
//
//    List<Department> findByFirmIdOrderByDisplayOrderAsc(UUID firmId);
//
//    Optional<Department> findByFirmIdAndCode(UUID firmId, String code);
//
//    boolean existsByFirmIdAndCode(UUID firmId, String code);
//
//    @Query("SELECT COUNT(e) FROM EmployeeProfile e WHERE e.department.id = :departmentId")
//    long countEmployeesByDepartmentId(@Param("departmentId") UUID departmentId);
//}