package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRole(Role role);

    @Query("SELECT rp.permission FROM RolePermission rp WHERE rp.role.id = :roleId AND rp.permission.active = true")
    List<Permission> findPermissionsByRoleId(@Param("roleId") UUID roleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RolePermission rp WHERE rp.role.id = :roleId")
    void deleteByRoleId(@Param("roleId") UUID roleId);

    /** Targeted strip — cascade narrowing removes specific permissions, not the whole set. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RolePermission rp WHERE rp.role.id = :roleId AND rp.permission.id IN :permissionIds")
    void deleteByRoleIdAndPermissionIdIn(@Param("roleId") UUID roleId, @Param("permissionIds") Collection<UUID> permissionIds);

    /** Batch-load permissions for multiple roles — eliminates N+1 in role listing. */
    @Query("SELECT rp FROM RolePermission rp WHERE rp.role.id IN :roleIds AND rp.permission.active = true")
    List<RolePermission> findByRoleIdIn(@Param("roleIds") List<UUID> roleIds);
}