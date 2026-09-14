package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirmServiceTrialTest {

    @Mock FirmRepository firmRepository;
    @Mock AuditService auditService;
    @InjectMocks FirmServiceImpl firmService;

    private Firm trialFirm(UUID id) {
        Firm f = new Firm();
        f.setId(id);
        f.setLawFirmCode("TEST01");
        f.setName("Test Firm");
        f.setStatus(FirmStatus.TRIAL);
        f.setIsTrial(true);
        f.setTrialDays(15);
        f.setTrialStartedAt(LocalDateTime.now().minusDays(5));
        f.setTrialExpiresAt(LocalDateTime.now().plusDays(10));
        return f;
    }

    private Firm activeFirm(UUID id) {
        Firm f = new Firm();
        f.setId(id);
        f.setLawFirmCode("PERM01");
        f.setName("Permanent Firm");
        f.setStatus(FirmStatus.ACTIVE);
        f.setIsTrial(false);
        return f;
    }

    // ── Suspend ────────────────────────────────────────────────────────

    @Test
    void suspendFirm_setsStatusToSuspended() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(activeFirm(id)));

        firmService.suspendFirm(id);

        verify(firmRepository).save(argThat(f -> f.getStatus() == FirmStatus.SUSPENDED));
        verify(auditService).logExplicit(eq(id), isNull(), eq("S"),
                eq(com.lawfirm.erp.common.enums.AuditAction.FIRM_SUSPENDED),
                eq(com.lawfirm.erp.common.enums.AuditEntity.FIRM), eq(id), anyString(), isNull());
    }

    @Test
    void suspendFirm_throwsIfNotFound() {
        when(firmRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> firmService.suspendFirm(UUID.randomUUID()));
    }

    // ── Activate ───────────────────────────────────────────────────────

    @Test
    void activateFirm_setsStatusToActive() {
        UUID id = UUID.randomUUID();
        Firm f = activeFirm(id);
        f.setStatus(FirmStatus.SUSPENDED);
        when(firmRepository.findById(id)).thenReturn(Optional.of(f));

        firmService.activateFirm(id);

        verify(firmRepository).save(argThat(firm -> firm.getStatus() == FirmStatus.ACTIVE));
    }

    @Test
    void activateFirm_restoresTrialIfStillValid() {
        UUID id = UUID.randomUUID();
        Firm f = trialFirm(id);
        f.setStatus(FirmStatus.SUSPENDED);
        when(firmRepository.findById(id)).thenReturn(Optional.of(f));

        firmService.activateFirm(id);

        verify(firmRepository).save(argThat(firm -> firm.getStatus() == FirmStatus.TRIAL));
    }

    // ── Extend Trial ───────────────────────────────────────────────────

    @Test
    void extendTrial_extendsExpiry() {
        UUID id = UUID.randomUUID();
        Firm f = trialFirm(id);
        LocalDateTime originalExpiry = f.getTrialExpiresAt();
        when(firmRepository.findById(id)).thenReturn(Optional.of(f));

        firmService.extendTrial(id, 7);

        verify(firmRepository).save(argThat(firm ->
                firm.getTrialExpiresAt().isAfter(originalExpiry)
                        && firm.getTrialDays() == 22
        ));
    }

    @Test
    void extendTrial_reactivatesIfExpired() {
        UUID id = UUID.randomUUID();
        Firm f = trialFirm(id);
        f.setStatus(FirmStatus.SUSPENDED);
        f.setTrialExpiresAt(LocalDateTime.now().minusDays(1));
        when(firmRepository.findById(id)).thenReturn(Optional.of(f));

        firmService.extendTrial(id, 5);

        verify(firmRepository).save(argThat(firm -> firm.getStatus() == FirmStatus.TRIAL));
    }

    @Test
    void extendTrial_throwsIfNotTrial() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(activeFirm(id)));

        assertThrows(BusinessRuleException.class, () -> firmService.extendTrial(id, 7));
    }

    @Test
    void extendTrial_throwsIfDaysNotPositive() {
        assertThrows(BusinessRuleException.class, () -> firmService.extendTrial(UUID.randomUUID(), 0));
    }

    // ── Convert to Permanent ───────────────────────────────────────────

    @Test
    void convertToPermanent_clearsTrialFields() {
        UUID id = UUID.randomUUID();
        Firm f = trialFirm(id);
        when(firmRepository.findById(id)).thenReturn(Optional.of(f));

        firmService.convertToPermanent(id);

        verify(firmRepository).save(argThat(firm ->
                !firm.getIsTrial()
                        && firm.getTrialExpiresAt() == null
                        && firm.getStatus() == FirmStatus.ACTIVE
        ));
    }

    @Test
    void convertToPermanent_throwsIfNotFound() {
        when(firmRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> firmService.convertToPermanent(UUID.randomUUID()));
    }
}
