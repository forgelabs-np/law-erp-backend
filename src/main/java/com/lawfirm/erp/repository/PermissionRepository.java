package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    boolean existsByCode(String code);
}
