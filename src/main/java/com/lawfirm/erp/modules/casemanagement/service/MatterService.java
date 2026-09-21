package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.dto.request.*;
import com.lawfirm.erp.modules.casemanagement.dto.response.*;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MatterService {

    MatterResponse createMatter(CreateMatterRequest request);

    /** {@code clientUserId} filters to one client's matters; ignored for client-scoped callers. */
    Page<MatterResponse> listMatters(MatterType matterType, MatterStatus status,
                                     UUID clientUserId, String search, int page, int size);

    MatterResponse getMatter(String matterNumber);

    List<TimelineEventResponse> getTimeline(String matterNumber);

    Page<TimelineEventResponse> getFirmTimeline(MatterType matterType, MatterStatus status,
                                                 LocalDate from, LocalDate to,
                                                 int page, int size);

    Page<StaleMatterResponse> getStaleMatters(int days, int page, int size);

    MatterResponse updateMatter(String matterNumber, UpdateMatterRequest request);

    MatterResponse addCourtCase(String matterNumber, AddCourtCaseRequest request);

    MatterResponse addParty(String matterNumber, PartyEntryRequest request);
}
