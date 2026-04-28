package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySubdomain(String subdomain);
    boolean existsBySubdomain(String subdomain);
}
