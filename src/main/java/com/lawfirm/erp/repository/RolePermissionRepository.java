package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);
    void deleteByRoleId(Long roleId);
    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);
}