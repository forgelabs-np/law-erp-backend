package com.lawfirm.erp.customer.repository;

import com.lawfirm.erp.customer.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByUserId(UUID userId);

    Optional<CustomerProfile> findByNationalId(String nationalId);
}