package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.AppealDeadlineRule;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppealDeadlineRuleRepository extends JpaRepository<AppealDeadlineRule, UUID> {

    Optional<AppealDeadlineRule> findByCourtLevelAppealedFromAndMatterTypeAndPartyIsState(
            CourtLevel courtLevel, MatterType matterType, boolean partyIsState);
}
