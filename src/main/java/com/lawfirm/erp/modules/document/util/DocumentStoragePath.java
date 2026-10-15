package com.lawfirm.erp.modules.document.util;

import java.util.UUID;

public final class DocumentStoragePath {

    public static final int DEFAULT_MAX_FILENAME_LENGTH = 120;

    private DocumentStoragePath() {
    }

    public static String forCase(UUID firmId, String matterNumber, String objectId, String filename) {
        return forCase(firmId, matterNumber, objectId, filename, DEFAULT_MAX_FILENAME_LENGTH);
    }

    public static String forCase(UUID firmId, String matterNumber, String objectId,
                                 String filename, int maxFilenameLength) {
        return "firms/" + firmId + "/cases/" + segment(matterNumber) + "/"
                + segment(objectId) + "/" + safeFilename(filename, maxFilenameLength);
    }

    public static String forProject(UUID firmId, String projectCode, String objectId, String filename) {
        return forProject(firmId, projectCode, objectId, filename, DEFAULT_MAX_FILENAME_LENGTH);
    }

    public static String forProject(UUID firmId, String projectCode, String objectId,
                                    String filename, int maxFilenameLength) {
        return "firms/" + firmId + "/projects/" + segment(projectCode) + "/"
                + segment(objectId) + "/" + safeFilename(filename, maxFilenameLength);
    }

    public static String prefixFor(UUID firmId) {
        return "firms/" + firmId + "/";
    }

    static String segment(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }

    static String safeFilename(String filename) {
        return safeFilename(filename, DEFAULT_MAX_FILENAME_LENGTH);
    }

    static String safeFilename(String filename, int maxFilenameLength) {
        int limit = maxFilenameLength > 0 ? maxFilenameLength : DEFAULT_MAX_FILENAME_LENGTH;
        String name = filename == null ? "" : filename.replaceAll("^.*[/\\\\]", "");
        name = name.replaceAll("[\\x00-\\x1f\\x7f]", "_");
        name = name.replaceAll("[^A-Za-z0-9._\\- ()\\[\\]]", "_");
        name = name.replaceAll("_{2,}", "_");
        name = name.replaceAll("^[.]+", "");
        name = name.strip();

        if (name.isBlank()) {
            name = "file";
        }
        if (name.length() > limit) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 ? name.substring(dot) : "";
            int keep = Math.max(1, limit - extension.length());
            name = name.substring(0, Math.min(keep, name.length())) + extension;
        }
        return name;
    }
}
