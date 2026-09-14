package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MatterResponse {

    private UUID id;

    private String matterNumber;

    private MatterType matterType;

    private String title;

    private MatterStatus status;

    private UUID currentCourtCaseId;

    private UUID assignedPartnerId;

    private CourtLevel originatingCourtLevel;

    private String description;

    private List<CourtCaseResponse> courtCases = new ArrayList<>();

    private List<MatterPartyResponse> parties = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
