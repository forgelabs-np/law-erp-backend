package com.lawfirm.erp.modules.document.util;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.modules.document.support.DocumentTestFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTypePolicyTest {

    @Nested
    @DisplayName("content type whitelist")
    class ContentTypes {

        @ParameterizedTest
        @ValueSource(strings = {
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain",
                "text/csv",
                "image/jpeg",
                "image/png",
                "image/tiff",
                "application/zip"})
        void allowsWhitelistedTypes(String contentType) {
            assertTrue(DocumentTypePolicy.isAllowedContentType(contentType));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "application/x-msdownload",
                "application/javascript",
                "text/html",
                "image/svg+xml",
                "application/x-sh",
                ""})
        void rejectsEverythingElse(String contentType) {
            assertFalse(DocumentTypePolicy.isAllowedContentType(contentType));
        }

        @Test
        @DisplayName("a charset parameter does not defeat the whitelist")
        void normalisesContentTypeParameters() {
            assertEquals("text/plain", DocumentTypePolicy.normalizeContentType("text/plain; charset=UTF-8"));
            assertEquals("application/pdf", DocumentTypePolicy.normalizeContentType("  APPLICATION/PDF  "));
            assertTrue(DocumentTypePolicy.isAllowedContentType(
                    DocumentTypePolicy.normalizeContentType("text/plain;charset=utf-8")));
        }

        @Test
        @DisplayName("the stored extension comes from the content type, not the filename")
        void extensionIsDerivedFromContentType() {
            assertEquals("pdf", DocumentTypePolicy.extensionFor("application/pdf"));
            assertEquals("docx", DocumentTypePolicy.extensionFor(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        }

        @Test
        void rejectsUnsupportedContentType() {
            assertThrows(BusinessRuleException.class,
                    () -> DocumentTypePolicy.requireAllowedContentType("application/x-msdownload"));
        }
    }

    @Nested
    @DisplayName("filename validation")
    class Filenames {

        @Test
        void acceptsAnAllowedExtension() {
            DocumentTypePolicy.validateFilename("petition.pdf");
            DocumentTypePolicy.validateFilename("evidence photo.JPEG");
        }

        @Test
        @DisplayName("an executable is refused even though it has an extension")
        void rejectsDisallowedExtension() {
            assertThrows(BusinessRuleException.class,
                    () -> DocumentTypePolicy.validateFilename("invoice.exe"));
        }

        @Test
        void rejectsNoExtension() {
            assertThrows(BusinessRuleException.class,
                    () -> DocumentTypePolicy.validateFilename("README"));
        }

        @Test
        void rejectsPathSeparators() {
            assertThrows(BusinessRuleException.class,
                    () -> DocumentTypePolicy.validateFilename("../../etc/passwd.pdf"));
            assertThrows(BusinessRuleException.class,
                    () -> DocumentTypePolicy.validateFilename("folder\\file.pdf"));
        }

        @Test
        void rejectsBlank() {
            assertThrows(BusinessRuleException.class, () -> DocumentTypePolicy.validateFilename("  "));
            assertThrows(BusinessRuleException.class, () -> DocumentTypePolicy.validateFilename(null));
        }

        @Test
        void rejectsAnOverlongName() {
            assertThrows(BusinessRuleException.class,
                    () -> DocumentTypePolicy.validateFilename("x".repeat(256) + ".pdf"));
        }

        @ParameterizedTest
        @DisplayName("every extension the content-type whitelist can produce is also accepted here")
        @ValueSource(strings = {"petition.pdf", "scan.tiff", "bundle.zip", "evidence.jpeg",
                "notes.txt", "ledger.csv", "draft.docx"})
        void filenameGateAgreesWithContentTypeGate(String filename) {
            DocumentTypePolicy.validateFilename(filename);
        }

        @ParameterizedTest
        @DisplayName("a type with no allowed content type is refused at the filename gate")
        @ValueSource(strings = {"animation.gif", "logo.svg"})
        void rejectsExtensionWithNoAllowedContentType(String filename) {
            assertThrows(BusinessRuleException.class,
                    () -> DocumentTypePolicy.validateFilename(filename));
        }

        @Test
        @DisplayName("the advertised extension list is stable and contains the common types")
        void advertisesTheAcceptedExtensions() {
            String csv = DocumentTypePolicy.allowedExtensionsAsCsv();
            assertEquals(csv, DocumentTypePolicy.allowedExtensionsAsCsv());
            for (String expected : new String[]{"pdf", "docx", "xlsx", "jpg", "jpeg", "png",
                    "txt", "csv", "tiff", "zip"}) {
                assertTrue(csv.contains(expected), "expected '" + expected + "' in " + csv);
            }
        }
    }

    @Nested
    @DisplayName("file signature (magic bytes)")
    class Signatures {

        @Test
        void pdfBytesMatchPdfContentType() {
            assertTrue(DocumentTypePolicy.matchesContent("application/pdf", DocumentTestFiles.pdf("body")));
        }

        @Test
        @DisplayName("an executable renamed to .pdf with a spoofed header is caught")
        void executableDoesNotMatchPdfContentType() {
            assertFalse(DocumentTypePolicy.matchesContent(
                    "application/pdf", DocumentTestFiles.windowsExecutable()));
        }

        @Test
        void jpegAndPngAreRecognised() {
            assertTrue(DocumentTypePolicy.matchesContent("image/jpeg", DocumentTestFiles.jpeg()));
            assertTrue(DocumentTypePolicy.matchesContent("image/png", DocumentTestFiles.png()));
        }

        @Test
        @DisplayName("ooxml formats are ZIP containers and share the ZIP signature")
        void zipContainerCoversOoxml() {
            byte[] docx = DocumentTestFiles.zip("word/document.xml");

            assertTrue(DocumentTypePolicy.matchesContent(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx));
            assertTrue(DocumentTypePolicy.matchesContent("application/zip", docx));
        }

        @Test
        void plainTextIsAcceptedButBinaryIsNot() {
            assertTrue(DocumentTypePolicy.matchesContent("text/plain", DocumentTestFiles.text("hello")));
            assertFalse(DocumentTypePolicy.matchesContent("text/plain", DocumentTestFiles.binaryWithNulBytes()));
        }

        @Test
        void emptyContentNeverMatches() {
            assertFalse(DocumentTypePolicy.matchesContent("application/pdf", new byte[0]));
            assertFalse(DocumentTypePolicy.matchesContent("application/pdf", null));
        }

        @Test
        @DisplayName("a truncated header shorter than the signature cannot pass")
        void truncatedHeaderCannotPass() {
            assertFalse(DocumentTypePolicy.matchesContent("application/pdf", new byte[]{0x25, 0x50}));
        }
    }
}
