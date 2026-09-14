package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT COUNT(rp) FROM RolePermission rp WHERE rp.permission.id = :permissionId")
    long countRolePermissionsByPermissionId(@Param("permissionId") UUID permissionId);

    @Query("SELECT COUNT(mp) FROM ModulePermission mp WHERE mp.permission.id = :permissionId")
    long countModulePermissionsByPermissionId(@Param("permissionId") UUID permissionId);

    /** Active-only list — avoids loading + filtering in Java. */
    @Query("SELECT p FROM Permission p WHERE p.active = true")
    List<Permission> findAllActive();
}