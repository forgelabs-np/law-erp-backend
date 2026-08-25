package com.lawfirm.erp.modules.usermanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.repository.CourtEventRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.scraper.repository.CourtRepository;
import com.lawfirm.erp.modules.scraper.repository.DailyHearingRepository;
import com.lawfirm.erp.modules.scraper.repository.HearingMatchRepository;
import com.lawfirm.erp.modules.scraper.repository.WeeklyHearingRepository;
import com.lawfirm.erp.modules.usermanagement.dto.response.GlobalDashboardResponse;
import com.lawfirm.erp.rbac.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalDashboardServiceTest {

    @Mock UserRepository userRepository;
    @Mock FirmRepository firmRepository;
    @Mock MatterRepository matterRepository;
    @Mock CourtEventRepository courtEventRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock CourtRepository courtRepository;
    @Mock DailyHearingRepository dailyHearingRepository;
    @Mock WeeklyHearingRepository weeklyHearingRepository;
    @Mock HearingMatchRepository hearingMatchRepository;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks GlobalDashboardServiceImpl service;

    private final UUID firmId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    /** Stub all trend queries to return empty (no data in the window). */
    private void stubEmptyTrends(UUID firmId) {
        List<Object[]> empty = new ArrayList<>();
        // User trends
        lenient().when(userRepository.countDailyByFirmIdAndDateRange(eq(firmId), any(), any()))
                .thenReturn(empty);
        lenient().when(userRepository.countDailyByDateRange(any(), any()))
                .thenReturn(empty);
        // Matter trends
        lenient().when(matterRepository.countDailyByFirmIdAndDateRange(eq(firmId), any(), any()))
                .thenReturn(empty);
        lenient().when(matterRepository.countDailyByDateRange(any(), any()))
                .thenReturn(empty);
        // Firm trends
        lenient().when(firmRepository.countDailyByDateRange(any(), any()))
                .thenReturn(empty);
    }

    private User user(UUID id, String roleCode, UserType userType, boolean active) {
        Role role = new Role();
        role.setRoleCode(roleCode);
        User u = User.builder().role(role).userType(userType).fullName("User " + roleCode).build();
        u.setId(id);
        u.setActive(active);
        return u;
    }

    private Firm firm(UUID id, FirmStatus status) {
        Firm f = new Firm();
        f.setId(id);
        f.setStatus(status);
        return f;
    }

    private Matter matter(UUID id, MatterStatus status) {
        Matter m = new Matter();
        m.setId(id);
        m.setFirmId(firmId);
        m.setStatus(status);
        return m;
    }

    @Test
    void firmAdminGetsFirmScopedData() {
        FirmContextHolder.set(firmId, "APX");
        when(currentUserResolver.isSuperAdmin()).thenReturn(false);
        stubEmptyTrends(firmId);

        // UserStats — uses COUNT queries now
        when(userRepository.countByFirmId(firmId)).thenReturn(1L);
        when(userRepository.countActiveByFirmId(firmId)).thenReturn(1L);
        when(userRepository.countByFirmIdAndRoleCode(firmId, "ADVOCATE")).thenReturn(1L);
        when(userRepository.countByFirmIdAndRoleCode(firmId, "PARALEGAL")).thenReturn(0L);
        when(userRepository.findByFirmIdAndUserType(firmId, UserType.CLIENT)).thenReturn(List.of());
        when(userRepository.countByFirmIdAndRoleCode(firmId, "FIRM_ADMIN")).thenReturn(0L);

        // FirmStats
        when(firmRepository.findById(firmId)).thenReturn(Optional.of(firm(firmId, FirmStatus.ACTIVE)));

        // CaseStats
        when(matterRepository.findByFirmId(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(matter(UUID.randomUUID(), MatterStatus.ACTIVE))));
        when(matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.ACTIVE)).thenReturn(1L);
        when(matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.CLOSED)).thenReturn(0L);
        when(courtEventRepository.findByFirmIdAndScheduledDate(eq(firmId), any(LocalDate.class)))
                .thenReturn(List.of());

        // Audit + Scraper
        when(auditLogRepository.findRecentByFirm(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(courtRepository.count()).thenReturn(1L);
        when(dailyHearingRepository.count()).thenReturn(10L);
        when(weeklyHearingRepository.count()).thenReturn(5L);
        when(hearingMatchRepository.count()).thenReturn(2L);
        when(dailyHearingRepository.findMaxScrapedDate()).thenReturn(Optional.of(LocalDate.now()));

        GlobalDashboardResponse resp = service.getDashboard();

        assertEquals(1, resp.getUserStats().getTotalUsers());
        assertEquals(1, resp.getUserStats().getTotalAdvocates());
        assertEquals(1, resp.getFirmStats().getTotalFirms());
        assertEquals(1, resp.getCaseStats().getTotalMatters());
        assertEquals(10, resp.getScraperStats().getTotalDailyHearings());
        assertEquals(2, resp.getScraperStats().getTotalMatches());
    }

    @Test
    void superAdminGetsAllFirmsData() {
        when(currentUserResolver.isSuperAdmin()).thenReturn(true);
        stubEmptyTrends(null);

        // UserStats — uses count queries
        when(userRepository.count()).thenReturn(3L);
        when(userRepository.countByUserType(UserType.FIRM_USER)).thenReturn(2L);
        when(userRepository.countByUserType(UserType.SUPER_ADMIN)).thenReturn(1L);
        when(userRepository.countByUserType(UserType.CLIENT)).thenReturn(0L);

        // FirmStats
        when(firmRepository.count()).thenReturn(2L);
        when(firmRepository.countByStatus(FirmStatus.ACTIVE)).thenReturn(1L);

        // CaseStats — for super admin, firmId is null so count() and findAll() are used
        when(matterRepository.count()).thenReturn(2L);
        when(matterRepository.findAll()).thenReturn(
                List.of(matter(UUID.randomUUID(), MatterStatus.ACTIVE),
                        matter(UUID.randomUUID(), MatterStatus.CLOSED)));
        when(courtEventRepository.findByScheduledDate(any(LocalDate.class))).thenReturn(List.of());

        // Audit + Scraper
        when(auditLogRepository.findRecent(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(courtRepository.count()).thenReturn(0L);
        when(dailyHearingRepository.count()).thenReturn(0L);
        when(weeklyHearingRepository.count()).thenReturn(0L);
        when(hearingMatchRepository.count()).thenReturn(0L);
        when(dailyHearingRepository.findMaxScrapedDate()).thenReturn(Optional.empty());

        GlobalDashboardResponse resp = service.getDashboard();

        assertEquals(3, resp.getUserStats().getTotalUsers());
        assertEquals(2, resp.getFirmStats().getTotalFirms());
        assertEquals(1, resp.getFirmStats().getSuspendedFirms());
        assertEquals(2, resp.getCaseStats().getTotalMatters());
        assertEquals(0, resp.getCaseStats().getClosedMatters());
    }

    @Test
    void nonSuperAdminWithoutFirmContextThrows() {
        when(currentUserResolver.isSuperAdmin()).thenReturn(false);
        // FirmContextHolder not set

        assertThrows(ForbiddenException.class, () -> service.getDashboard());
        verify(userRepository, never()).findAll();
    }

    @Test
    void recentActivityResolvesUserNames() {
        FirmContextHolder.set(firmId, "APX");
        when(currentUserResolver.isSuperAdmin()).thenReturn(false);
        stubEmptyTrends(firmId);

        UUID userId = UUID.randomUUID();

        // UserStats — count queries
        when(userRepository.countByFirmId(firmId)).thenReturn(0L);
        when(userRepository.countActiveByFirmId(firmId)).thenReturn(0L);
        when(userRepository.countByFirmIdAndRoleCode(firmId, "ADVOCATE")).thenReturn(0L);
        when(userRepository.countByFirmIdAndRoleCode(firmId, "PARALEGAL")).thenReturn(0L);
        when(userRepository.findByFirmIdAndUserType(firmId, UserType.CLIENT)).thenReturn(List.of());
        when(userRepository.countByFirmIdAndRoleCode(firmId, "FIRM_ADMIN")).thenReturn(0L);

        // FirmStats
        when(firmRepository.findById(firmId)).thenReturn(Optional.of(firm(firmId, FirmStatus.ACTIVE)));

        // CaseStats
        when(matterRepository.findByFirmId(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.ACTIVE)).thenReturn(0L);
        when(matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.CLOSED)).thenReturn(0L);
        when(courtEventRepository.findByFirmIdAndScheduledDate(eq(firmId), any(LocalDate.class)))
                .thenReturn(List.of());

        // Audit + Scraper
        AuditLog log = AuditLog.builder()
                .userId(userId)
                .action(AuditAction.USER_CREATED)
                .entityType(AuditEntity.USER)
                .summary("Employee created")
                .build();
        when(auditLogRepository.findRecentByFirm(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(userRepository.findAllById(List.of(userId)))
                .thenReturn(List.of(user(userId, "ADVOCATE", UserType.FIRM_USER, true)));
        when(courtRepository.count()).thenReturn(0L);
        when(dailyHearingRepository.count()).thenReturn(0L);
        when(weeklyHearingRepository.count()).thenReturn(0L);
        when(hearingMatchRepository.count()).thenReturn(0L);
        when(dailyHearingRepository.findMaxScrapedDate()).thenReturn(Optional.empty());

        GlobalDashboardResponse resp = service.getDashboard();

        assertEquals(1, resp.getRecentActivity().size());
        assertEquals("Employee created", resp.getRecentActivity().get(0).getSummary());
        assertEquals("User ADVOCATE", resp.getRecentActivity().get(0).getUserName());
    }

    @Test
    void trendsReturnCumulativeData() {
        FirmContextHolder.set(firmId, "APX");
        when(currentUserResolver.isSuperAdmin()).thenReturn(false);

        // UserStats
        when(userRepository.countByFirmId(firmId)).thenReturn(5L);
        when(userRepository.countActiveByFirmId(firmId)).thenReturn(5L);
        when(userRepository.countByFirmIdAndRoleCode(firmId, "ADVOCATE")).thenReturn(3L);
        when(userRepository.countByFirmIdAndRoleCode(firmId, "PARALEGAL")).thenReturn(1L);
        when(userRepository.findByFirmIdAndUserType(firmId, UserType.CLIENT)).thenReturn(List.of());
        when(userRepository.countByFirmIdAndRoleCode(firmId, "FIRM_ADMIN")).thenReturn(1L);

        // FirmStats
        when(firmRepository.findById(firmId)).thenReturn(Optional.of(firm(firmId, FirmStatus.ACTIVE)));

        // CaseStats
        when(matterRepository.findByFirmId(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.ACTIVE)).thenReturn(2L);
        when(matterRepository.countByFirmIdAndStatus(firmId, MatterStatus.CLOSED)).thenReturn(0L);
        when(courtEventRepository.findByFirmIdAndScheduledDate(eq(firmId), any(LocalDate.class)))
                .thenReturn(List.of());

        // Audit + Scraper
        when(auditLogRepository.findRecentByFirm(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(courtRepository.count()).thenReturn(0L);
        when(dailyHearingRepository.count()).thenReturn(0L);
        when(weeklyHearingRepository.count()).thenReturn(0L);
        when(hearingMatchRepository.count()).thenReturn(0L);
        when(dailyHearingRepository.findMaxScrapedDate()).thenReturn(Optional.empty());

        // ── Trend data: simulate 2 users created on day 1, 1 more on day 3 ──
        LocalDate day1 = LocalDate.now().minusDays(5);
        LocalDate day3 = LocalDate.now().minusDays(3);
        List<Object[]> userRows = new ArrayList<>();
        userRows.add(new Object[]{Date.valueOf(day1), 2L, 2L, 0L, 0L});
        userRows.add(new Object[]{Date.valueOf(day3), 1L, 1L, 0L, 0L});
        when(userRepository.countDailyByFirmIdAndDateRange(eq(firmId), any(), any()))
                .thenReturn(userRows);

        // Matter trends: 1 matter created on day 2
        LocalDate day2 = LocalDate.now().minusDays(4);
        List<Object[]> matterRows = new ArrayList<>();
        matterRows.add(new Object[]{Date.valueOf(day2), 1L, 1L, 0L});
        when(matterRepository.countDailyByFirmIdAndDateRange(eq(firmId), any(), any()))
                .thenReturn(matterRows);

        // Firm trends: empty
        List<Object[]> emptyRows = new ArrayList<>();
        when(firmRepository.countDailyByDateRange(any(), any())).thenReturn(emptyRows);

        // Use 7-day window so we get a nice spread
        GlobalDashboardResponse resp = service.getDashboard(7);

        // Verify user trends: cumulative
        assertNotNull(resp.getUserTrends());
        assertEquals(8, resp.getUserTrends().size()); // 7 days + today = 8 entries
        // Last entry should show cumulative: 2 + 1 = 3 total users
        GlobalDashboardResponse.UserTrend lastUserTrend = resp.getUserTrends().get(resp.getUserTrends().size() - 1);
        assertEquals(3L, lastUserTrend.getTotalUsers());
        assertEquals(3L, lastUserTrend.getActiveUsers());

        // Verify matter trends
        assertNotNull(resp.getMatterTrends());
        assertEquals(8, resp.getMatterTrends().size());
        GlobalDashboardResponse.MatterTrend lastMatterTrend = resp.getMatterTrends().get(resp.getMatterTrends().size() - 1);
        assertEquals(1L, lastMatterTrend.getTotalMatters());
        assertEquals(1L, lastMatterTrend.getActiveMatters());

        // Verify firm trends: all zeros (empty data)
        assertNotNull(resp.getFirmTrends());
        assertEquals(8, resp.getFirmTrends().size());
        assertEquals(0L, resp.getFirmTrends().get(0).getTotalFirms());
    }
}
