package com.lawfirm.erp.modules.document.support;

import java.nio.charset.StandardCharsets;

/** Byte content with genuine file signatures, so the magic-byte checks are exercised for real. */
public final class DocumentTestFiles {

    private DocumentTestFiles() {
    }

    public static byte[] pdf(String body) {
        return ("%PDF-1.7\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    /** docx/xlsx/pptx/zip all begin with the ZIP local file header. */
    public static byte[] zip(String body) {
        String content = "PK\u0003\u0004" + body;
        return content.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
    }

    public static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    }

    /** An executable: Windows "MZ" header. Must never pass as a PDF. */
    public static byte[] windowsExecutable() {
        return new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
    }

    public static byte[] text(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    /** Binary content wearing a text/plain label. */
    public static byte[] binaryWithNulBytes() {
        return new byte[]{0x41, 0x42, 0x00, 0x43, 0x00};
    }
}
