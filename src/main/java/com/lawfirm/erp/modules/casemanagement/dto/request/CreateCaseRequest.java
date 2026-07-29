package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.BailStatus;
import com.lawfirm.erp.modules.casemanagement.enums.CaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateCaseRequest {

    @NotNull(message = "Case type is required")
    private CaseType caseType;

    @NotBlank(message = "Title is required")
    private String title;

    private String courtName;
    private String courtCaseNumber;

    private LocalDate filingDate;
    private String filingNumber;

    private UUID assignedTo;

    private String description;

    // Civil-specific
    private LocalDate mediationDate;
    private String mediationOutcome;
    private LocalDate writtenStatementDeadline;

    // Criminal-specific
    private String firNumber;
    private LocalDate firDate;
    private String policeStation;
    private String investigationAuthority;
    private LocalDate arrestDate;
    private LocalDate chargeSheetDate;
    private BailStatus bailStatus;

    private List<PartyEntry> plaintiffs;
    private List<PartyEntry> defendants;

    @Data
    public static class PartyEntry {
        private String fullName;
        private String mobileNo;
        private String email;
        private String address;
        private String representation;
        private UUID clientId;
        private boolean isOurClient;
        private UUID advocateId;
        private String notes;
    }
}
