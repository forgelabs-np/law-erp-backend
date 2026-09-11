package com.lawfirm.erp.tenant.repository;

import com.lawfirm.erp.tenant.entity.TenantType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TenantTypeRepository extends JpaRepository<TenantType, UUID> {
    Optional<TenantType> findByCode(String code);
    boolean existsByCode(String code);
}