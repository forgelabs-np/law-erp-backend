package com.lawfirm.erp.modules.email.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record HearingReminderDetails(
        String firmName,
        String matterNumber,
        String matterTitle,
        String courtCaseRef,
        String courtName,
        LocalDate scheduledDate,
        LocalTime scheduledTime,
        String courtRoom,
        String judgeName) {
}
