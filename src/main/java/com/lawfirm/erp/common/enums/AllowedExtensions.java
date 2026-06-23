package com.lawfirm.erp.common.enums;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum AllowedExtensions {
    PDF("pdf"),
    DOC("doc"),
    DOCX("docx"),
    XLS("xls"),
    XLSX("xlsx"),
    PPT("ppt"),
    PPTX("pptx"),
    JPG("jpg"),
    JPEG("jpeg"),
    PNG("png"),
    GIF("gif"),
    SVG("svg"),
    TXT("txt"),
    CSV("csv");

    private final String extension;

    AllowedExtensions(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    public static Set<String> getAllExtensions() {
        return Arrays.stream(values())
                .map(AllowedExtensions::getExtension)
                .collect(Collectors.toSet());
    }

    public static Set<String> getExtensionsAsSet() {
        return new HashSet<>(getAllExtensions());
    }

    public static boolean isValid(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        return getAllExtensions().contains(extension.trim().toLowerCase());
    }

    public static String getDefaultExtensions() {
        return "pdf,doc,docx,jpg,png";
    }

    public static String getAsCsv() {
        return String.join(",", getAllExtensions());
    }
}