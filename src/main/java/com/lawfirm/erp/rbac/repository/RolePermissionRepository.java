package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findByRole(Role role);
    void deleteByRole(Role role);
}