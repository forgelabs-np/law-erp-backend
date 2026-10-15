package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.modules.document.support.FakeStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FirmStorageServiceTest {

    private static final UUID FIRM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private FakeStorageService storage;
    private FirmStorageService service;

    @BeforeEach
    void setUp() {
        storage = new FakeStorageService();
        service = new FirmStorageService(storage);
    }

    private MockMultipartFile file(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    @Test
    @DisplayName("The stored key keeps the real extension — a .jpg logo is not served as logo.png")
    void storeFirmLogo_usesOriginalExtension() {
        FirmStorageService.LogoUploadResult result =
                service.storeFirmLogo(FIRM_ID, file("logo.JPG", "image/jpeg", new byte[]{1, 2, 3}));

        String expectedKey = "firms/" + FIRM_ID + "/logo.jpg";
        assertEquals(expectedKey, result.storedKey());
        assertTrue(storage.has(expectedKey), "bytes should land under the extension-aware key");
        assertEquals("http://fake-storage/logo/" + expectedKey, result.presignUrl());
    }

    @Test
    void storeFirmLogo_rejectsNonImageExtension() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.storeFirmLogo(FIRM_ID, file("logo.txt", "text/plain", new byte[]{1})));

        assertTrue(ex.getMessage().contains("must be an image"));
        assertTrue(storage.objects.isEmpty(), "nothing should be written for a rejected type");
    }

    @Test
    void storeFirmLogo_rejectsFileWithoutExtension() {
        assertThrows(BusinessRuleException.class,
                () -> service.storeFirmLogo(FIRM_ID, file("logo", "image/png", new byte[]{1})));
    }

    @Test
    void storeFirmLogo_rejectsOversizedLogo() {
        byte[] tooBig = new byte[200 * 1024 + 1];

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.storeFirmLogo(FIRM_ID, file("logo.png", "image/png", tooBig)));

        assertTrue(ex.getMessage().contains("at most"));
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void storeFirmLogo_rejectsEmptyFile() {
        assertThrows(BusinessRuleException.class,
                () -> service.storeFirmLogo(FIRM_ID, file("logo.png", "image/png", new byte[0])));
    }

    @Test
    @DisplayName("Deleting the prior logo recovers the key from a presigned URL (query string stripped)")
    void deleteFirmLogo_stripsPresignQueryString() {
        String presigned = "http://localhost:9000/tarikh-documents/firms/" + FIRM_ID
                + "/logo.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc";

        service.deleteFirmLogo(FIRM_ID, presigned);

        assertEquals(1, storage.deletedKeys.size());
        assertEquals("firms/" + FIRM_ID + "/logo.png", storage.deletedKeys.get(0));
    }

    @Test
    void deleteFirmLogo_ignoresBlankUrl() {
        service.deleteFirmLogo(FIRM_ID, null);
        service.deleteFirmLogo(FIRM_ID, "   ");

        assertTrue(storage.deletedKeys.isEmpty());
    }
}
