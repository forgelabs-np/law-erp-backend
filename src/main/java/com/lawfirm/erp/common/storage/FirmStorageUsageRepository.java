package com.lawfirm.erp.common.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FirmStorageUsageRepository extends JpaRepository<FirmStorageUsage, Long> {

    Optional<FirmStorageUsage> findByFirmId(UUID firmId);

    /**
     * Row lock held for the rest of the transaction — the serialization point that stops two
     * concurrent uploads from both fitting into the same remaining space.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM FirmStorageUsage u WHERE u.firmId = :firmId")
    Optional<FirmStorageUsage> findForUpdate(@Param("firmId") UUID firmId);
}
