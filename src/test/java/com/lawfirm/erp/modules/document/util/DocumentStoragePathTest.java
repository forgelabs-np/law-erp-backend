package com.lawfirm.erp.modules.document.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentStoragePathTest {

    private static final UUID FIRM = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String OBJECT = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test
    @DisplayName("a case document is stored under its case number")
    void casePathUsesMatterNumber() {
        String path = DocumentStoragePath.forCase(FIRM, "MT-2026-00041", OBJECT, "petition.pdf");

        assertEquals("firms/" + FIRM + "/cases/MT-2026-00041/" + OBJECT + "/petition.pdf", path);
    }

    @Test
    @DisplayName("a project document is stored under its project code")
    void projectPathUsesProjectCode() {
        String path = DocumentStoragePath.forProject(FIRM, "ABC-PRJ-2026-00007", OBJECT, "agreement.pdf");

        assertEquals("firms/" + FIRM + "/projects/ABC-PRJ-2026-00007/" + OBJECT + "/agreement.pdf", path);
    }

    @Test
    @DisplayName("every path starts with the firm prefix, which is what the upload policy pins")
    void pathsStayInsideTheFirmPrefix() {
        assertTrue(DocumentStoragePath.forCase(FIRM, "MT-1", OBJECT, "a.pdf")
                .startsWith(DocumentStoragePath.prefixFor(FIRM)));
        assertTrue(DocumentStoragePath.forProject(FIRM, "P-1", OBJECT, "a.pdf")
                .startsWith(DocumentStoragePath.prefixFor(FIRM)));
    }

    @Test
    @DisplayName("a filename cannot introduce path segments")
    void filenameCannotEscapeTheFolder() {
        String path = DocumentStoragePath.forCase(FIRM, "MT-1", OBJECT, "../../etc/passwd.pdf");

        assertEquals("firms/" + FIRM + "/cases/MT-1/" + OBJECT + "/passwd.pdf", path);
        assertFalse(path.contains(".."));
        assertFalse(path.contains("etc/"));
    }

    @Test
    @DisplayName("a Windows-style path is reduced to the bare filename")
    void windowsPathIsStripped() {
        assertEquals("contract.pdf",
                DocumentStoragePath.safeFilename("C:\\Users\\someone\\contract.pdf"));
    }

    @Test
    @DisplayName("leading dots and control characters are neutralised")
    void dangerousCharactersAreNeutralised() {
        String safe = DocumentStoragePath.safeFilename(".hidden\u0000name.pdf");

        assertFalse(safe.startsWith("."));
        assertFalse(safe.contains("\u0000"));
    }

    @Test
    @DisplayName("a case number that is not path-safe is still stable")
    void caseNumberIsSegmented() {
        String path = DocumentStoragePath.forCase(FIRM, "MT/2026/41", OBJECT, "a.pdf");

        assertTrue(path.contains("/cases/MT_2026_41/"));
    }

    @Test
    @DisplayName("a very long filename keeps its extension")
    void longFilenameIsTruncatedButKeepsExtension() {
        String longName = "x".repeat(300) + ".pdf";
        String safe = DocumentStoragePath.safeFilename(longName);

        assertTrue(safe.length() <= 120);
        assertTrue(safe.endsWith(".pdf"));
    }

    @Test
    @DisplayName("a blank filename falls back rather than producing an empty segment")
    void blankFilenameFallsBack() {
        assertEquals("file", DocumentStoragePath.safeFilename("..."));
    }

    @Test
    @DisplayName("the truncation length is caller-supplied (system_config-driven), not fixed")
    void maxFilenameLengthIsConfigurable() {
        String longName = "y".repeat(300) + ".pdf";

        String shortPath = DocumentStoragePath.forCase(FIRM, "MT-1", OBJECT, longName, 40);
        String shortName = shortPath.substring(shortPath.lastIndexOf('/') + 1);

        assertTrue(shortName.length() <= 40);
        assertTrue(shortName.endsWith(".pdf"));
        assertEquals(DocumentStoragePath.DEFAULT_MAX_FILENAME_LENGTH,
                DocumentStoragePath.safeFilename(longName).length());
    }
}
