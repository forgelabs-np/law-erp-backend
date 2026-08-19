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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks GlobalDashboardService service;

    private final UUID firmId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
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

        UUID userId = UUID.randomUUID();
        when(userRepository.findByFirmId(firmId))
                .thenReturn(List.of(user(userId, "ADVOCATE", UserType.FIRM_USER, true)));
        when(firmRepository.findAllById(List.of(firmId)))
                .thenReturn(List.of(firm(firmId, FirmStatus.ACTIVE)));
        when(matterRepository.findByFirmId(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(matter(UUID.randomUUID(), MatterStatus.ACTIVE))));
        when(courtEventRepository.findByFirmIdAndScheduledDate(eq(firmId), any(LocalDate.class)))
                .thenReturn(List.of());
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
        verify(userRepository).findByFirmId(firmId);
        verify(matterRepository).findByFirmId(eq(firmId), any(Pageable.class));
    }

    @Test
    void superAdminGetsAllFirmsData() {
        when(currentUserResolver.isSuperAdmin()).thenReturn(true);

        when(userRepository.findAll())
                .thenReturn(List.of(
                        user(UUID.randomUUID(), "ADVOCATE", UserType.FIRM_USER, true),
                        user(UUID.randomUUID(), "PARALEGAL", UserType.FIRM_USER, true),
                        user(UUID.randomUUID(), "FIRM_ADMIN", UserType.FIRM_USER, true)));
        when(firmRepository.findAll())
                .thenReturn(List.of(firm(UUID.randomUUID(), FirmStatus.ACTIVE),
                        firm(UUID.randomUUID(), FirmStatus.SUSPENDED)));
        when(matterRepository.findAll())
                .thenReturn(List.of(matter(UUID.randomUUID(), MatterStatus.ACTIVE),
                        matter(UUID.randomUUID(), MatterStatus.CLOSED)));
        when(courtEventRepository.findByScheduledDate(any(LocalDate.class))).thenReturn(List.of());
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
        assertEquals(1, resp.getCaseStats().getClosedMatters());
        verify(userRepository).findAll();
        verify(firmRepository).findAll();
        verify(matterRepository).findAll();
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

        UUID userId = UUID.randomUUID();
        when(userRepository.findByFirmId(firmId)).thenReturn(List.of());
        when(firmRepository.findAllById(List.of(firmId))).thenReturn(List.of());
        when(matterRepository.findByFirmId(eq(firmId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(courtEventRepository.findByFirmIdAndScheduledDate(eq(firmId), any(LocalDate.class)))
                .thenReturn(List.of());

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
}
