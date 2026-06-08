package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    @Modifying
    void deleteByRole(Role role);

    List<RolePermission> findByRole(Role role);

    @Query("SELECT rp.permission FROM RolePermission rp WHERE rp.role.id = :roleId AND rp.permission.active = true")
    List<Permission> findPermissionsByRoleId(@Param("roleId") UUID roleId);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.role.id = :roleId")
    void deleteByRoleId(@Param("roleId") UUID roleId);
}