package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.CourtCaseRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CourtCaseRoleRepository extends JpaRepository<CourtCaseRole, UUID> {

    List<CourtCaseRole> findByCourtCaseIdAndFirmId(UUID courtCaseId, UUID firmId);

    List<CourtCaseRole> findByMatterPartyIdAndFirmId(UUID matterPartyId, UUID firmId);

    List<CourtCaseRole> findByCourtCaseIdInAndFirmId(Collection<UUID> courtCaseIds, UUID firmId);
}
