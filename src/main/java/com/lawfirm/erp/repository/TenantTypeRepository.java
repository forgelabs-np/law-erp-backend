package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.TenantType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TenantTypeRepository extends JpaRepository<TenantType, Long> {
    Optional<TenantType> findByCode(String code);
    boolean existsByCode(String code);
}