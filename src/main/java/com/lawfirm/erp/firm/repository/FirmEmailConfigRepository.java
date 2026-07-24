package com.lawfirm.erp.firm.repository;

import com.lawfirm.erp.firm.entity.FirmEmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FirmEmailConfigRepository extends JpaRepository<FirmEmailConfig, UUID> {

    Optional<FirmEmailConfig> findByFirmId(UUID firmId);

    boolean existsByFirmId(UUID firmId);

    void deleteByFirmId(UUID firmId);
}
