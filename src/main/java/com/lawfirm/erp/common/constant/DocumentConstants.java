package com.lawfirm.erp.common.constant;

public final class DocumentConstants {

    public static final String UPLOAD_SUMMARY = "Upload a document";
    public static final String UPLOAD_DESCRIPTION =
            "multipart/form-data. Send the file as the `file` part, plus matterNumber for a case "
            + "document or projectCode for a project document — exactly one. Optional courtCaseRef "
            + "tags a specific court instance of the matter. The file is stored and the document is "
            + "ACTIVE in this single call — there is no separate confirm step.";

    public static final String LIST_LIBRARY_SUMMARY = "Browse the document library";
    public static final String LIST_LIBRARY_DESCRIPTION =
            "Firm admins see every document in the firm; staff see only their assigned cases and "
            + "projects; clients see only their own shared documents.";

    public static final String LIST_CASE_DOCUMENTS_SUMMARY = "List a case's documents";
    public static final String LIST_PROJECT_DOCUMENTS_SUMMARY = "List a project's documents";
    public static final String DOWNLOAD_SUMMARY = "Get a download link";
    public static final String DOWNLOAD_DESCRIPTION =
            "Returns a short-lived presigned URL. The link is a credential — do not log or share it.";
    public static final String SET_VISIBILITY_SUMMARY = "Change document visibility";
    public static final String SET_VISIBILITY_DESCRIPTION =
            "PRIVATE keeps a document internal; SHARED is what makes it visible to the client.";
    public static final String ARCHIVE_SUMMARY = "Archive a document";
    public static final String ARCHIVE_DESCRIPTION =
            "Soft delete. The document stops counting against storage but the stored file is kept "
            + "for legal retention — it is never physically destroyed here.";
    public static final String STORAGE_USAGE_SUMMARY = "Get this firm's storage usage";
    public static final String LIST_MY_DOCUMENTS_SUMMARY = "List my documents (client portal)";
    public static final String SET_FIRM_QUOTA_SUMMARY = "Allocate storage to a firm";
    public static final String SET_FIRM_QUOTA_DESCRIPTION =
            "Sets how many bytes the firm may store. 0 means unlimited. Applies to new uploads only; "
            + "existing documents above the new allocation are not deleted.";
    public static final String GET_FIRM_QUOTA_SUMMARY = "Get a firm's storage usage";

    private DocumentConstants() {
    }
}
