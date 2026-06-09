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
    boolean existsByModuleIdAndAction(UUID moduleId, PermissionAction action);

    List<Permission> findByModuleId(UUID moduleId);

    @Query("SELECT COUNT(p) FROM Permission p WHERE p.module.id = :moduleId AND p.active = true")
    long countByModuleId(@Param("moduleId") UUID moduleId);

    @Query("SELECT COUNT(rp) FROM RolePermission rp WHERE rp.permission.id = :permissionId")
    long countRolePermissionsByPermissionId(@Param("permissionId") UUID permissionId);
}