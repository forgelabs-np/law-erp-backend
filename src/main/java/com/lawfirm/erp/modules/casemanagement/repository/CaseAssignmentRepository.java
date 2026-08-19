package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.CaseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseAssignmentRepository extends JpaRepository<CaseAssignment, UUID> {

    List<CaseAssignment> findByMatterIdAndFirmId(UUID matterId, UUID firmId);

    List<CaseAssignment> findByUserIdAndFirmId(UUID userId, UUID firmId);

    List<CaseAssignment> findByMatterIdAndUserIdAndFirmId(UUID matterId, UUID userId, UUID firmId);

    Optional<CaseAssignment> findByMatterIdAndUserIdAndAssignmentRoleAndFirmId(
            UUID matterId, UUID userId, com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole role, UUID firmId);

    boolean existsByMatterIdAndUserIdAndFirmId(UUID matterId, UUID userId, UUID firmId);

    @Query("SELECT ca.matterId FROM CaseAssignment ca WHERE ca.userId = :userId AND ca.firmId = :firmId")
    List<UUID> findMatterIdsByUserIdAndFirmId(@Param("userId") UUID userId, @Param("firmId") UUID firmId);

    void deleteByMatterIdAndFirmId(UUID matterId, UUID firmId);
}
