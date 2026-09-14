package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import com.lawfirm.erp.dto.firm.response.FirmListResponse;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirmServiceGetAllTest {

    @Mock FirmRepository firmRepository;
    @Mock AuditService auditService;
    @InjectMocks FirmServiceImpl firmService;

    private Firm buildFirm(String code, String name, boolean isTrial, FirmStatus status) {
        Firm f = new Firm();
        f.setId(UUID.randomUUID());
        f.setLawFirmCode(code);
        f.setName(name);
        f.setFirmType(FirmType.FIRM);
        f.setStatus(status);
        f.setIsTrial(isTrial);
        f.setTrialDays(isTrial ? 15 : null);
        f.setTrialStartedAt(isTrial ? LocalDateTime.now().minusDays(5) : null);
        f.setTrialExpiresAt(isTrial ? LocalDateTime.now().plusDays(10) : null);
        f.setCreatedAt(LocalDateTime.now().minusDays(30));
        return f;
    }

    @Test
    @DisplayName("getAllFirms returns all firms with isTrial flag")
    void getAllFirms_includesIsTrial() {
        Firm trialFirm = buildFirm("TRIAL01", "Trial Firm", true, FirmStatus.TRIAL);
        Firm permFirm = buildFirm("PERM01", "Permanent Firm", false, FirmStatus.ACTIVE);
        when(firmRepository.findAll()).thenReturn(List.of(trialFirm, permFirm));

        List<FirmListResponse> result = firmService.getAllFirms();

        assertEquals(2, result.size());

        FirmListResponse trial = result.stream()
                .filter(f -> f.getLawFirmCode().equals("TRIAL01")).findFirst().orElseThrow();
        assertTrue(trial.getIsTrial());
        assertEquals(15, trial.getTrialDays());
        assertNotNull(trial.getTrialExpiresAt());
        assertEquals(FirmStatus.TRIAL, trial.getStatus());

        FirmListResponse perm = result.stream()
                .filter(f -> f.getLawFirmCode().equals("PERM01")).findFirst().orElseThrow();
        assertFalse(perm.getIsTrial());
        assertNull(perm.getTrialDays());
        assertNull(perm.getTrialExpiresAt());
        assertEquals(FirmStatus.ACTIVE, perm.getStatus());
    }

    @Test
    @DisplayName("getAllFirms returns empty list when no firms exist")
    void getAllFirms_emptyList() {
        when(firmRepository.findAll()).thenReturn(List.of());

        List<FirmListResponse> result = firmService.getAllFirms();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAllFirms includes all firm fields")
    void getAllFirms_allFieldsPresent() {
        Firm firm = buildFirm("TEST01", "Test Firm", false, FirmStatus.ACTIVE);
        firm.setEmail("test@test.com");
        firm.setPhone("9800000000");
        firm.setAddress("Kathmandu");
        firm.setJurisdiction("Nepal");
        when(firmRepository.findAll()).thenReturn(List.of(firm));

        List<FirmListResponse> result = firmService.getAllFirms();

        assertEquals(1, result.size());
        FirmListResponse response = result.get(0);
        assertNotNull(response.getId());
        assertEquals("TEST01", response.getLawFirmCode());
        assertEquals("Test Firm", response.getName());
        assertEquals("test@test.com", response.getEmail());
        assertEquals("9800000000", response.getPhone());
        assertEquals("Kathmandu", response.getAddress());
        assertEquals("Nepal", response.getJurisdiction());
        assertNotNull(response.getCreatedAt());
    }
}
