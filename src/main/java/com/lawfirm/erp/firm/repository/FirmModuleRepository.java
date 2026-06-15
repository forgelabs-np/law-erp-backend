package com.lawfirm.erp.firm.repository;

import com.lawfirm.erp.firm.entity.FirmModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FirmModuleRepository extends JpaRepository<FirmModule, UUID> {

    Optional<FirmModule> findByFirmIdAndModuleId(UUID firmId, UUID moduleId);

    @Query("SELECT fm FROM FirmModule fm JOIN FETCH fm.module WHERE fm.firm.id = :firmId")
    List<FirmModule> findByFirmIdWithModule(@Param("firmId") UUID firmId);

    @Query("SELECT fm FROM FirmModule fm JOIN FETCH fm.module WHERE fm.firm.id = :firmId AND fm.isEnabled = true")
    List<FirmModule> findEnabledModulesByFirmId(@Param("firmId") UUID firmId);

    @Query("SELECT CASE WHEN COUNT(fm) > 0 THEN true ELSE false END " +
            "FROM FirmModule fm JOIN fm.module m " +
            "WHERE fm.firm.id = :firmId AND m.code = :moduleCode AND fm.isEnabled = true")
    boolean existsByFirmIdAndModuleCodeAndIsEnabledTrue(@Param("firmId") UUID firmId,
                                                        @Param("moduleCode") String moduleCode);
}