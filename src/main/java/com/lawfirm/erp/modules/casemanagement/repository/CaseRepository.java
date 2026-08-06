package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CaseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<Case, UUID> {

    Page<Case> findByFirmId(UUID firmId, Pageable pageable);

    Page<Case> findByFirmIdAndCaseType(UUID firmId, CaseType caseType, Pageable pageable);

    Page<Case> findByFirmIdAndCaseStage(UUID firmId, CaseStage caseStage, Pageable pageable);

    Page<Case> findByFirmIdAndStatus(UUID firmId, CaseStatus status, Pageable pageable);

    Page<Case> findByFirmIdAndAssignedTo(UUID firmId, UUID assignedTo, Pageable pageable);

    @Query("SELECT c FROM Case c WHERE c.firmId = :firmId " +
           "AND (:caseType IS NULL OR c.caseType = :caseType) " +
           "AND (:caseStage IS NULL OR c.caseStage = :caseStage) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:assignedTo IS NULL OR c.assignedTo = :assignedTo) " +
           "AND (:courtName IS NULL OR LOWER(c.courtName) LIKE LOWER(CONCAT('%', :courtName, '%'))) " +
           "AND (:dateFrom IS NULL OR c.filingDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR c.filingDate <= :dateTo) " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :search, '%'))" +
           "     OR LOWER(c.filingNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Case> findByFilters(@Param("firmId") UUID firmId,
                             @Param("caseType") CaseType caseType,
                             @Param("caseStage") CaseStage caseStage,
                             @Param("status") CaseStatus status,
                             @Param("assignedTo") UUID assignedTo,
                             @Param("courtName") String courtName,
                             @Param("dateFrom") LocalDate dateFrom,
                             @Param("dateTo") LocalDate dateTo,
                             @Param("search") String search,
                             Pageable pageable);

    @Query("SELECT c.caseNumber FROM Case c WHERE c.firmId = :firmId " +
           "AND c.caseNumber LIKE :pattern ORDER BY c.caseNumber DESC")
    List<String> findMaxCaseNumberByPattern(@Param("firmId") UUID firmId,
                                             @Param("pattern") String pattern,
                                             Pageable pageable);

    Optional<Case> findByIdAndFirmId(UUID id, UUID firmId);

    Optional<Case> findByCaseNumberAndFirmId(String caseNumber, UUID firmId);
}
