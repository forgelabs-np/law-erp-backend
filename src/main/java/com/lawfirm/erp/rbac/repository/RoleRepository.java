package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByRoleCode(String roleCode);

    Optional<Role> findByRoleCodeAndFirmId(String roleCode, UUID firmId);

    Optional<Role> findByRoleName(String roleName);

    List<Role> findByFirmId(UUID firmId);

    List<Role> findByFirmIsNull();

    boolean existsByRoleCode(String roleCode);

    boolean existsByRoleName(String roleName);
}