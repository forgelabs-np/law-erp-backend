package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);
    boolean existsByCode(String code);
}