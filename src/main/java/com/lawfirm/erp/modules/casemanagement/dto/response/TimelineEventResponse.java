package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TimelineEventResponse {
    private UUID id;
    private UUID caseId;
    private String caseNumber;
    private String caseTitle;
    private TimelineEventType eventType;
    private String title;
    private String description;
    private LocalDateTime createdAt;
    private UUID createdBy;
}
