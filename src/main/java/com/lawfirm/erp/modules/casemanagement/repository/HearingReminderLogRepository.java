package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.HearingReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface HearingReminderLogRepository extends JpaRepository<HearingReminderLog, UUID> {

    boolean existsByCourtEventIdAndRecipientTypeAndRecipientEmailAndScheduledDate(
            UUID courtEventId, HearingReminderLog.RecipientType recipientType,
            String recipientEmail, LocalDate scheduledDate);
}
