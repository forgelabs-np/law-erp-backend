package com.lawfirm.erp.firm.repository;

import com.lawfirm.erp.firm.entity.Firm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FirmRepository extends JpaRepository<Firm, UUID> {
    Optional<Firm> findByLawFirmCode(String lawFirmCode);
    Optional<Firm> findByName(String name);
    boolean existsByLawFirmCode(String lawFirmCode);
}