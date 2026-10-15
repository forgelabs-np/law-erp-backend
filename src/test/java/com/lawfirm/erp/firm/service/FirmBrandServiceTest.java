package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.dto.firm.request.SetFirmThemeRequest;
import com.lawfirm.erp.dto.firm.response.FirmProfileResponse;
import com.lawfirm.erp.dto.firm.response.LogoResponse;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirmBrandServiceTest {

    @Mock private FirmRepository firmRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private AuditService auditService;
    @Mock private FirmStorageService firmStorageService;

    @InjectMocks private FirmBrandService firmBrandService;

    private static final UUID FIRM_ID = UUID.randomUUID();

    private Firm firm;

    @BeforeEach
    void setUp() {
        firm = new Firm();
        firm.setId(FIRM_ID);
        firm.setLawFirmCode("YLAW");
        firm.setName("YLaw & Partners");
        firm.setLogoAllowed(false);
    }

    private void givenCurrentFirm() {
        when(currentUserResolver.getCurrentFirmId()).thenReturn(FIRM_ID);
        when(firmRepository.findById(FIRM_ID)).thenReturn(Optional.of(firm));
    }

    // ── Theme ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A theme write normalizes both hex values and reports the firm as personalized")
    void setMyFirmTheme_normalizesAndMarksPersonal() {
        givenCurrentFirm();
        when(firmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SetFirmThemeRequest request = new SetFirmThemeRequest();
        request.setBrandPrimaryHex("0D47A1");
        request.setBrandSecondaryHex("#E3F2FD");

        FirmProfileResponse response = firmBrandService.setMyFirmTheme(request);

        assertEquals("#0d47a1", firm.getBrandPrimaryHex());
        assertEquals("#e3f2fd", firm.getBrandSecondaryHex());
        assertTrue(response.getIsPersonalColor());
        verify(auditService).log(eq(AuditAction.FIRM_UPDATED), eq(AuditEntity.FIRM), eq(FIRM_ID), anyString());
    }

    @Test
    @DisplayName("Omitting a hex leaves that color untouched (a partial edit form)")
    void setMyFirmTheme_partialUpdateLeavesOtherColor() {
        givenCurrentFirm();
        firm.setBrandSecondaryHex("#e3f2fd");
        when(firmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SetFirmThemeRequest request = new SetFirmThemeRequest();
        request.setBrandPrimaryHex("#112233");

        firmBrandService.setMyFirmTheme(request);

        assertEquals("#112233", firm.getBrandPrimaryHex());
        assertEquals("#e3f2fd", firm.getBrandSecondaryHex());
    }

    @Test
    void setMyFirmTheme_rejectsInvalidHex() {
        givenCurrentFirm();

        SetFirmThemeRequest request = new SetFirmThemeRequest();
        request.setBrandPrimaryHex("not-a-color");

        assertThrows(BusinessRuleException.class, () -> firmBrandService.setMyFirmTheme(request));
        verify(firmRepository, never()).save(any());
    }

    @Test
    void getMyFirmProfile_marksFirmPersonalOnlyWhenAColorIsSet() {
        givenCurrentFirm();

        FirmProfileResponse none = firmBrandService.getMyFirmProfile();
        assertFalse(none.getIsPersonalColor());
        assertNull(none.getBrandPrimaryHex());

        firm.setBrandPrimaryHex("#1a237e");
        FirmProfileResponse one = firmBrandService.getMyFirmProfile();
        assertTrue(one.getIsPersonalColor());
        assertEquals("#1a237e", one.getBrandPrimaryHex());
    }

    // ── Logo toggle ────────────────────────────────────────────────────────

    @Test
    void toggleLogoAllowed_flipsTheFlag() {
        givenCurrentFirm();
        when(firmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LogoResponse response = firmBrandService.toggleLogoAllowed(true);

        assertTrue(response.isLogoAllowed());
        assertTrue(firm.getLogoAllowed());
        verify(auditService).log(eq(AuditAction.FIRM_UPDATED), eq(AuditEntity.FIRM), eq(FIRM_ID), anyString());
    }

    @Test
    void toggleLogoAllowed_rejectsNull() {
        givenCurrentFirm();

        assertThrows(BusinessRuleException.class, () -> firmBrandService.toggleLogoAllowed(null));
        verify(firmRepository, never()).save(any());
    }

    // ── Logo upload ────────────────────────────────────────────────────────

    @Test
    @DisplayName("A firm that has not enabled logos cannot upload one")
    void uploadMyFirmLogo_rejectedWhenNotAllowed() {
        givenCurrentFirm();
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});

        assertThrows(ForbiddenException.class, () -> firmBrandService.uploadMyFirmLogo(file));
        verifyNoInteractions(firmStorageService);
        verify(firmRepository, never()).save(any());
    }

    @Test
    void uploadMyFirmLogo_rejectsEmptyFile() {
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);

        assertThrows(BusinessRuleException.class, () -> firmBrandService.uploadMyFirmLogo(file));
        verifyNoInteractions(firmStorageService);
    }

    @Test
    void uploadMyFirmLogo_storesNewLogoAndRemovesThePreviousOne() {
        givenCurrentFirm();
        firm.setLogoAllowed(true);
        firm.setLogoUrl("http://old-host/bucket/firms/" + FIRM_ID + "/logo.png?sig=old");
        when(firmStorageService.storeFirmLogo(eq(FIRM_ID), any()))
                .thenReturn(new FirmStorageService.LogoUploadResult(
                        "firms/" + FIRM_ID + "/logo.webp", "http://new-host/logo.webp"));
        when(firmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MultipartFile file = new MockMultipartFile("file", "logo.webp", "image/webp", new byte[]{9, 9});
        LogoResponse response = firmBrandService.uploadMyFirmLogo(file);

        assertEquals("http://new-host/logo.webp", firm.getLogoUrl());
        assertEquals("http://new-host/logo.webp", response.getLogoUrl());
        verify(firmStorageService).deleteFirmLogo(FIRM_ID, "http://old-host/bucket/firms/" + FIRM_ID + "/logo.png?sig=old");
        verify(auditService).log(eq(AuditAction.FIRM_UPDATED), eq(AuditEntity.FIRM), eq(FIRM_ID), anyString());
    }

    @Test
    void uploadMyFirmLogo_firstUploadSkipsDelete() {
        givenCurrentFirm();
        firm.setLogoAllowed(true);
        when(firmStorageService.storeFirmLogo(eq(FIRM_ID), any()))
                .thenReturn(new FirmStorageService.LogoUploadResult(
                        "firms/" + FIRM_ID + "/logo.png", "http://new-host/logo.png"));
        when(firmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1});
        firmBrandService.uploadMyFirmLogo(file);

        verify(firmStorageService, never()).deleteFirmLogo(any(), any());
    }

    @Test
    void getMyFirmLogo_returnsTheStoredLogoAndFlag() {
        givenCurrentFirm();
        firm.setLogoAllowed(true);
        firm.setLogoUrl("http://host/logo.png?sig=1");

        LogoResponse response = firmBrandService.getMyFirmLogo();

        assertEquals("http://host/logo.png?sig=1", response.getLogoUrl());
        assertTrue(response.isLogoAllowed());
    }
}
