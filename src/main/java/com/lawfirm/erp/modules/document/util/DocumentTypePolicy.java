package com.lawfirm.erp.modules.document.util;

import com.lawfirm.erp.common.exception.BusinessRuleException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The upload whitelist: what may be stored, and what it must actually look like.
 *
 * <p>A declared {@code Content-Type} is attacker-controlled, so it is only the first gate.
 * {@link #matchesContent} checks the file's own signature bytes at confirm time, which is what
 * stops an executable renamed to {@code petition.pdf} with a spoofed header.
 */
public final class DocumentTypePolicy {

    /** content type → canonical extension. The extension stored is derived from this, never from the filename. */
    private static final Map<String, String> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
            Map.entry("text/plain", "txt"),
            Map.entry("text/csv", "csv"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/tiff", "tiff"),
            Map.entry("application/zip", "zip")
    );

    /**
     * Extensions this store accepts, derived from {@link #ALLOWED_TYPES} so the filename gate and
     * the content-type gate can never drift apart. {@code jpeg} is added explicitly because it is a
     * common alias for the canonical {@code jpg}.
     */
    private static final Set<String> ALLOWED_EXTENSIONS = allowedExtensions();

    private static final byte[] SIGNATURE_PDF = {0x25, 0x50, 0x44, 0x46};                 // %PDF
    private static final byte[] SIGNATURE_ZIP = {0x50, 0x4B};                             // PK — also docx/xlsx/pptx
    private static final byte[] SIGNATURE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] SIGNATURE_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};          // .PNG
    private static final byte[] SIGNATURE_TIFF_LE = {0x49, 0x49, 0x2A, 0x00};
    private static final byte[] SIGNATURE_TIFF_BE = {0x4D, 0x4D, 0x00, 0x2A};
    private static final byte[] SIGNATURE_OLE = {                                        // legacy .doc/.xls/.ppt
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private DocumentTypePolicy() {
    }

    private static Set<String> allowedExtensions() {
        Set<String> extensions = new LinkedHashSet<>(ALLOWED_TYPES.values());
        extensions.add("jpeg");
        return Collections.unmodifiableSet(extensions);
    }

    /** The extensions accepted by {@link #validateFilename}, sorted for a stable message. */
    public static String allowedExtensionsAsCsv() {
        return ALLOWED_EXTENSIONS.stream().sorted().collect(Collectors.joining(","));
    }

    /** Strips any {@code ;charset=...} and normalises case. */
    public static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(';');
        return separator >= 0 ? normalized.substring(0, separator).trim() : normalized;
    }

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_TYPES.containsKey(contentType);
    }

    public static String extensionFor(String contentType) {
        return ALLOWED_TYPES.get(contentType);
    }

    /**
     * Rejects a filename that could not be stored safely or whose extension is off the
     * whitelist — {@code invoice.exe} is refused here even when the declared type is valid.
     */
    public static void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessRuleException("A filename is required");
        }
        if (filename.length() > 255) {
            throw new BusinessRuleException("The filename is longer than 255 characters");
        }
        if (filename.contains("/") || filename.contains("\\")) {
            throw new BusinessRuleException("The filename must not contain a path");
        }
        String extension = extensionOf(filename);
        if (extension.isEmpty()) {
            throw new BusinessRuleException("The file must have an extension. Allowed: "
                    + allowedExtensionsAsCsv());
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException("Files of type '." + extension
                    + "' are not accepted. Allowed: " + allowedExtensionsAsCsv());
        }
    }

    public static void requireAllowedContentType(String contentType) {
        if (!isAllowedContentType(contentType)) {
            throw new BusinessRuleException("Unsupported content type '" + contentType
                    + "'. Allowed types: " + String.join(", ", ALLOWED_TYPES.keySet()));
        }
    }

    public static String extensionOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** True when the object's leading bytes actually match the declared content type. */
    public static boolean matchesContent(String contentType, byte[] head) {
        if (head == null || head.length == 0) {
            return false;
        }
        return switch (contentType) {
            case "application/pdf" -> startsWith(head, SIGNATURE_PDF);
            case "application/zip",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    startsWith(head, SIGNATURE_ZIP);
            case "image/jpeg" -> startsWith(head, SIGNATURE_JPEG);
            case "image/png" -> startsWith(head, SIGNATURE_PNG);
            case "image/tiff" -> startsWith(head, SIGNATURE_TIFF_LE) || startsWith(head, SIGNATURE_TIFF_BE);
            case "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint" ->
                    startsWith(head, SIGNATURE_OLE);
            // Text has no signature; a NUL byte in the first bytes is the giveaway that it is binary.
            case "text/plain", "text/csv" -> !containsNul(head);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNul(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
