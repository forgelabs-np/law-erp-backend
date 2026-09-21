package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a court where the firm has active cases.
 * Used for scraper integration and court selection UI.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FirmCourtResponse {

    /**
     * The court's official name (e.g., "Kathmandu District Court")
     */
    private String courtName;

    /**
     * Court level (DISTRICT, HIGH, SUPREME)
     */
    private CourtLevel courtLevel;

    /**
     * Number of active cases at this court
     */
    private Long activeCaseCount;
}
