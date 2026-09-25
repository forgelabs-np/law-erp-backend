package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.firm.request.UpdateFirmRequest;
import com.lawfirm.erp.dto.firm.response.FirmListResponse;
import com.lawfirm.erp.entity.User;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirmServiceUpdateTest {

    @Mock FirmRepository firmRepository;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;
    @InjectMocks FirmServiceImpl firmService;

    private static final String CODE = "TEST01";

    private Firm firm(UUID id) {
        Firm f = new Firm();
        f.setId(id);
        f.setLawFirmCode(CODE);
        f.setName("Test Firm");
        f.setFirmType(FirmType.FIRM);
        f.setStatus(FirmStatus.ACTIVE);
        f.setEmail("firm@test.com");
        f.setPhone("9800000000");
        f.setAddress("Kathmandu");
        f.setJurisdiction("Nepal");
        f.setIsTrial(false);
        f.setCreatedAt(LocalDateTime.now().minusDays(30));
        return f;
    }

    private User admin(Firm firm, UUID id) {
        User u = new User();
        u.setId(id);
        u.setUsername("firmadmin");
        u.setEmail("admin@test.com");
        u.setMobileNo("9811111111");
        u.setFullName("Firm Admin");
        u.setUserType(UserType.FIRM);
        u.setFirm(firm);
        u.setCreatedAt(LocalDateTime.now().minusDays(30));
        return u;
    }

    /** No FIRM_ADMIN row exists for the firm unless a test asks for one. */
    private void noAdmin() {
        when(userRepository.findFirmAdminsByFirmId(any())).thenReturn(List.of());
    }

    // ── Firm fields ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Firm details update with no adminPassword in the payload (the reported bug)")
    void updateFirm_updatesFirmFieldsWithoutAdminPassword() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(firm(id)));
        noAdmin();

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setName("Renamed Firm");
        request.setFirmType(FirmType.SOLO);
        request.setEmail("new@firm.com");
        request.setPhone("9822222222");
        request.setAddress("Pokhara");
        request.setJurisdiction("Nepal");

        FirmListResponse response = firmService.updateFirm(id, request);

        verify(firmRepository).save(argThat(f ->
                f.getName().equals("Renamed Firm")
                        && f.getFirmType() == FirmType.SOLO
                        && f.getEmail().equals("new@firm.com")
                        && f.getPhone().equals("9822222222")
                        && f.getAddress().equals("Pokhara")));
        assertEquals("Renamed Firm", response.getName());
        assertEquals("new@firm.com", response.getEmail());
        assertEquals(CODE, response.getLawFirmCode());
    }

    @Test
    @DisplayName("Omitted fields are left alone — a partial edit form must not wipe columns")
    void updateFirm_omittedFieldsAreUntouched() {
        UUID id = UUID.randomUUID();
        Firm existing = firm(id);
        when(firmRepository.findById(id)).thenReturn(Optional.of(existing));
        noAdmin();

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setName("Only The Name");

        firmService.updateFirm(id, request);

        verify(firmRepository).save(argThat(f ->
                f.getName().equals("Only The Name")
                        && f.getEmail().equals("firm@test.com")
                        && f.getPhone().equals("9800000000")
                        && f.getAddress().equals("Kathmandu")
                        && f.getJurisdiction().equals("Nepal")
                        && f.getFirmType() == FirmType.FIRM));
    }

    @Test
    @DisplayName("A blank field clears that column (the console sends \"\" for an emptied input)")
    void updateFirm_blankFieldClearsColumn() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(firm(id)));
        noAdmin();

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAddress("   ");

        firmService.updateFirm(id, request);

        verify(firmRepository).save(argThat(f -> f.getAddress() == null));
    }

    @Test
    void updateFirm_writesAFirmUpdatedAuditEntry() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(firm(id)));
        noAdmin();

        firmService.updateFirm(id, new UpdateFirmRequest());

        verify(auditService).logExplicit(eq(id), isNull(), eq("S"),
                eq(AuditAction.FIRM_UPDATED), eq(AuditEntity.FIRM), eq(id), anyString(), isNull());
    }

    @Test
    void updateFirm_throwsIfFirmNotFound() {
        when(firmRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> firmService.updateFirm(UUID.randomUUID(), new UpdateFirmRequest()));
    }

    // ── Immutable fields ───────────────────────────────────────────────────

    @Test
    void updateFirm_throwsWhenLawFirmCodeChanges() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(firm(id)));
        noAdmin();

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setLawFirmCode("OTHER99");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> firmService.updateFirm(id, request));
        assertTrue(ex.getMessage().contains("Law firm code cannot be changed"));
        verify(firmRepository, never()).save(any());
    }

    @Test
    @DisplayName("Replaying the same lawFirmCode from the create body is accepted")
    void updateFirm_sameLawFirmCodeIsANoOp() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(firm(id)));
        noAdmin();

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setLawFirmCode(CODE);
        request.setAdminUsername("firmadmin");

        FirmListResponse response = firmService.updateFirm(id, request);

        assertEquals(CODE, response.getLawFirmCode());
        verify(firmRepository).save(any());
    }

    @Test
    @DisplayName("The admin password is refused, not silently dropped (AUTH-14 lesson)")
    void updateFirm_throwsWhenAdminPasswordSent() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(firm(id)));
        noAdmin();

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAdminPassword("BrandNewPass123!");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> firmService.updateFirm(id, request));
        assertTrue(ex.getMessage().contains("password cannot be changed here"));
        assertTrue(ex.getMessage().contains("reset-password"), "should point at the reset endpoint");
        verify(firmRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateFirm_throwsWhenAdminUsernameChanges() {
        UUID id = UUID.randomUUID();
        Firm existing = firm(id);
        when(firmRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.findFirmAdminsByFirmId(id)).thenReturn(List.of(admin(existing, UUID.randomUUID())));

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAdminUsername("somethingelse");

        assertThrows(BusinessRuleException.class, () -> firmService.updateFirm(id, request));
        verify(firmRepository, never()).save(any());
    }

    // ── Admin contact details ──────────────────────────────────────────────

    @Test
    void updateFirm_updatesAdminContactDetails() {
        UUID id = UUID.randomUUID();
        Firm existing = firm(id);
        User admin = admin(existing, UUID.randomUUID());
        when(firmRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.findFirmAdminsByFirmId(id)).thenReturn(List.of(admin));

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAdminFullName("Renamed Admin");
        request.setAdminEmail("newadmin@test.com");
        request.setAdminMobileNo("9899999999");

        firmService.updateFirm(id, request);

        verify(userRepository).save(argThat(u ->
                u.getFullName().equals("Renamed Admin")
                        && u.getEmail().equals("newadmin@test.com")
                        && u.getMobileNo().equals("9899999999")
                        && u.getUsername().equals("firmadmin")));
    }

    @Test
    @DisplayName("An unchanged admin email does not collide with itself")
    void updateFirm_unchangedAdminEmailSkipsUniquenessCheck() {
        UUID id = UUID.randomUUID();
        Firm existing = firm(id);
        User admin = admin(existing, UUID.randomUUID());
        when(firmRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.findFirmAdminsByFirmId(id)).thenReturn(List.of(admin));

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAdminEmail("admin@test.com");
        request.setAdminFullName("Firm Admin");

        firmService.updateFirm(id, request);

        verify(userRepository, never()).existsByEmailAndFirmId(anyString(), any());
        verify(userRepository).save(admin);
    }

    @Test
    void updateFirm_throwsWhenNewAdminEmailIsTakenInFirm() {
        UUID id = UUID.randomUUID();
        Firm existing = firm(id);
        User admin = admin(existing, UUID.randomUUID());
        when(firmRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.findFirmAdminsByFirmId(id)).thenReturn(List.of(admin));
        when(userRepository.existsByEmailAndFirmId("taken@test.com", id)).thenReturn(true);

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAdminEmail("taken@test.com");

        assertThrows(DuplicateResourceException.class, () -> firmService.updateFirm(id, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateFirm_throwsWhenNewAdminMobileIsTakenInFirm() {
        UUID id = UUID.randomUUID();
        Firm existing = firm(id);
        User admin = admin(existing, UUID.randomUUID());
        when(firmRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.findFirmAdminsByFirmId(id)).thenReturn(List.of(admin));
        when(userRepository.existsByMobileNoAndFirmId("9800000000", id)).thenReturn(true);

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAdminMobileNo("9800000000");

        assertThrows(DuplicateResourceException.class, () -> firmService.updateFirm(id, request));
    }

    @Test
    @DisplayName("Admin details for a firm with no FIRM_ADMIN row fail loudly instead of vanishing")
    void updateFirm_adminDetailsWithNoAdminAccountThrows() {
        UUID id = UUID.randomUUID();
        when(firmRepository.findById(id)).thenReturn(Optional.of(firm(id)));
        noAdmin();

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAdminFullName("Nobody");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> firmService.updateFirm(id, request));
        assertTrue(ex.getMessage().contains("FIRM_ADMIN"));
    }

    @Test
    @DisplayName("The oldest FIRM_ADMIN is the primary one when a firm has several")
    void updateFirm_targetsTheOldestAdmin() {
        UUID id = UUID.randomUUID();
        Firm existing = firm(id);
        User older = admin(existing, UUID.randomUUID());
        older.setUsername("oldadmin");
        older.setCreatedAt(LocalDateTime.now().minusDays(60));
        User newer = admin(existing, UUID.randomUUID());
        newer.setUsername("newadmin");
        newer.setCreatedAt(LocalDateTime.now().minusDays(1));
        when(firmRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.findFirmAdminsByFirmId(id)).thenReturn(List.of(newer, older));

        UpdateFirmRequest request = new UpdateFirmRequest();
        request.setAdminFullName("Whoever");

        firmService.updateFirm(id, request);

        verify(userRepository).save(argThat(u -> u.getUsername().equals("oldadmin")));
        verify(userRepository, never()).save(argThat(u -> u.getUsername().equals("newadmin")));
    }
}
