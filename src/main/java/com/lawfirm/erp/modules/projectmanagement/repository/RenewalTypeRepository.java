package com.lawfirm.erp.modules.projectmanagement.repository;

import com.lawfirm.erp.modules.projectmanagement.entity.RenewalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RenewalTypeRepository extends JpaRepository<RenewalType, Long> {

    /** System defaults (firm_id IS NULL) + firm-scoped custom types. */
    @Query("SELECT rt FROM RenewalType rt WHERE rt.active = true " +
           "AND (rt.firmId IS NULL OR rt.firmId = :firmId)")
    List<RenewalType> findAvailableForFirm(@Param("firmId") UUID firmId);

    List<RenewalType> findByFirmIdAndActive(UUID firmId, boolean active);

    boolean existsByNameAndFirmIdAndActive(String name, UUID firmId, boolean active);
}
