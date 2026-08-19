package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.AuthenticatedUser;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.casemanagement.dto.response.CasePositioningResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.DashboardResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock MatterRepository matterRepository;
    @Mock CourtCaseRepository courtCaseRepository;
    @Mock CourtEventRepository courtEventRepository;
    @Mock CaseAssignmentService assignmentService;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock UserRepository userRepository;

    @InjectMocks DashboardService dashboardService;

    private final UUID firmId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID advocateId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private AuthenticatedUser user(String... roles) {
        AuthenticatedUser u = new AuthenticatedUser();
        u.setId(adminId);
        u.setRoles(List.of(roles));
        return u;
    }

    private Matter matter(UUID id, String number) {
        Matter m = new Matter();
        m.setId(id);
        m.setFirmId(firmId);
        m.setMatterNumber(number);
        m.setTitle("Test matter " + number);
        m.setMatterType(MatterType.CIVIL);
        m.setStatus(MatterStatus.ACTIVE);
        return m;
    }

    private CourtCase courtCase(UUID id, UUID matterId) {
        CourtCase cc = new CourtCase();
        cc.setId(id);
        cc.setFirmId(firmId);
        cc.setMatterId(matterId);
        cc.setCourtName("Kathmandu District Court");
        cc.setOurCourtCaseRef("REF-" + id);
        cc.setStage(CourtCaseStage.HEARING_STAGE);
        cc.setStatus(CourtCaseStatus.ACTIVE);
        return cc;
    }

    @Test
    void firmAdminSeesAllMatters() {
        FirmContextHolder.set(firmId, "APEX-LAW");
        when(currentUserResolver.getCurrentUserId()).thenReturn(adminId);
        when(currentUserResolver.getCurrentUser()).thenReturn(user("FIRM_ADMIN"));

        Matter m1 = matter(UUID.randomUUID(), "M1");
        Matter m2 = matter(UUID.randomUUID(), "M2");
        when(matterRepository.findByFirmId(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1, m2)));
        when(courtEventRepository.findByFirmIdAndScheduledDate(eq(firmId), any(LocalDate.class)))
                .thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertEquals(2, resp.getStats().getTotalMatters());
        assertEquals(2, resp.getStats().getActiveMatters());
    }

    @Test
    void advocateSeesOnlyAssignedMatters() {
        FirmContextHolder.set(firmId, "APEX-LAW");
        when(currentUserResolver.getCurrentUserId()).thenReturn(advocateId);
        when(currentUserResolver.getCurrentUser()).thenReturn(user("ADVOCATE"));

        UUID assignedId = UUID.randomUUID();
        Matter assigned = matter(assignedId, "M1");
        when(assignmentService.getAssignedMatterIds(advocateId, firmId)).thenReturn(List.of(assignedId));
        when(matterRepository.findAllById(List.of(assignedId))).thenReturn(List.of(assigned));
        when(courtEventRepository.findByFirmIdAndAttendingAdvocateIdAndScheduledDate(
                eq(firmId), eq(advocateId), any(LocalDate.class))).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertEquals(1, resp.getStats().getTotalMatters());
    }

    @Test
    void advocateWithNoAssignmentsSeesNothing() {
        FirmContextHolder.set(firmId, "APEX-LAW");
        when(currentUserResolver.getCurrentUserId()).thenReturn(advocateId);
        when(currentUserResolver.getCurrentUser()).thenReturn(user("ADVOCATE"));
        when(assignmentService.getAssignedMatterIds(advocateId, firmId)).thenReturn(List.of());
        when(courtEventRepository.findByFirmIdAndAttendingAdvocateIdAndScheduledDate(
                eq(firmId), eq(advocateId), any(LocalDate.class))).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertEquals(0, resp.getStats().getTotalMatters());
        assertTrue(resp.getCasePositioning().isEmpty());
    }

    @Test
    void matterWithNoHearingIsStaleAndFlagMatchesList() {
        FirmContextHolder.set(firmId, "APEX-LAW");
        when(currentUserResolver.getCurrentUserId()).thenReturn(adminId);
        when(currentUserResolver.getCurrentUser()).thenReturn(user("FIRM_ADMIN"));

        UUID matterId = UUID.randomUUID();
        UUID ccId = UUID.randomUUID();
        Matter m = matter(matterId, "M1");
        m.setCurrentCourtCaseId(ccId);
        CourtCase cc = courtCase(ccId, matterId);

        when(matterRepository.findByFirmId(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m)));
        when(courtCaseRepository.findAllById(List.of(ccId))).thenReturn(List.of(cc));
        when(courtEventRepository.findLatestPeshiByCourtCaseIds(List.of(ccId))).thenReturn(List.<Object[]>of());
        when(courtEventRepository.countByCourtCaseIds(List.of(ccId))).thenReturn(List.<Object[]>of());
        when(courtEventRepository.findByCourtCaseIdInAndFirmIdOrderBySequenceNoAsc(List.of(ccId), firmId))
                .thenReturn(List.of());
        when(courtEventRepository.findByFirmIdAndScheduledDate(eq(firmId), any(LocalDate.class)))
                .thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertEquals(1, resp.getStats().getStaleCount());
        assertEquals(1, resp.getStaleCases().size());
        CasePositioningResponse stale = resp.getStaleCases().get(0);
        assertTrue(stale.isStale(), "stale flag must agree with appearing in staleCases");
        assertEquals(1, resp.getCasePositioning().size());
    }

    @Test
    void matterWithRecentHearingIsNotStale() {
        FirmContextHolder.set(firmId, "APEX-LAW");
        when(currentUserResolver.getCurrentUserId()).thenReturn(adminId);
        when(currentUserResolver.getCurrentUser()).thenReturn(user("FIRM_ADMIN"));

        UUID matterId = UUID.randomUUID();
        UUID ccId = UUID.randomUUID();
        Matter m = matter(matterId, "M1");
        m.setCurrentCourtCaseId(ccId);
        CourtCase cc = courtCase(ccId, matterId);

        when(matterRepository.findByFirmId(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m)));
        when(courtCaseRepository.findAllById(List.of(ccId))).thenReturn(List.of(cc));
        when(courtEventRepository.findLatestPeshiByCourtCaseIds(List.of(ccId)))
                .thenReturn(List.<Object[]>of(new Object[]{ccId, LocalDate.now().minusDays(2)}));
        when(courtEventRepository.countByCourtCaseIds(List.of(ccId))).thenReturn(List.<Object[]>of());
        when(courtEventRepository.findByCourtCaseIdInAndFirmIdOrderBySequenceNoAsc(List.of(ccId), firmId))
                .thenReturn(List.of());
        when(courtEventRepository.findByFirmIdAndScheduledDate(eq(firmId), any(LocalDate.class)))
                .thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertEquals(0, resp.getStats().getStaleCount());
        assertTrue(resp.getStaleCases().isEmpty());
        assertEquals(2, resp.getCasePositioning().get(0).getDaysSinceLastHearing());
    }
}
