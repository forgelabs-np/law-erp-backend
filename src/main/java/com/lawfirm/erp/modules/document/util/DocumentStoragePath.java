package com.lawfirm.erp.modules.document.util;

import java.util.UUID;

/**
 * Builds the readable object key for a document.
 *
 * <pre>
 * firms/{firmId}/cases/{matterNumber}/{objectId}/{filename}
 * firms/{firmId}/projects/{projectCode}/{objectId}/{filename}
 * </pre>
 *
 * <p>The case number / project code is in the path because it is what a human browsing the
 * console expects to see. The UUID folder keeps filenames from colliding, so the readable part
 * never has to be sanitised for uniqueness.
 *
 * <p>{@code firmId} is the first segment rather than a firm code: the top level has to be
 * immutable, and a firm code can be changed, which would orphan every object underneath it.
 */
public final class DocumentStoragePath {

    /**
     * Used only when no configured length is supplied. The live value comes from the STORAGE
     * config group ({@code DOCUMENT_MAX_FILENAME_LENGTH}) via the service, so an operator can
     * change it without a rebuild.
     */
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

    /** The firm's own prefix — the POST policy pins uploads inside it. */
    public static String prefixFor(UUID firmId) {
        return "firms/" + firmId + "/";
    }

    /** A path segment can only ever contain characters that are safe in an object key. */
    static String segment(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }

    /** Keeps the original name for humans while removing anything structural. */
    static String safeFilename(String filename) {
        return safeFilename(filename, DEFAULT_MAX_FILENAME_LENGTH);
    }

    /** As {@link #safeFilename(String)}, truncating to {@code maxFilenameLength} characters. */
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
