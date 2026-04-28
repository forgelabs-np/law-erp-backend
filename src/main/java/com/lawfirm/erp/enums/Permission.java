package com.lawfirm.erp.enums;

public enum Permission {
    // User management
    USER_CREATE("Create users"),
    USER_READ("Read users"),
    USER_UPDATE("Update users"),
    USER_DELETE("Delete users"),

    // Case management
    CASE_CREATE("Create cases"),
    CASE_READ("Read cases"),
    CASE_UPDATE("Update cases"),
    CASE_DELETE("Delete cases"),
    CASE_ASSIGN("Assign cases to lawyers"),

    // Client management
    CLIENT_CREATE("Create clients"),
    CLIENT_READ("Read clients"),
    CLIENT_UPDATE("Update clients"),
    CLIENT_DELETE("Delete clients"),

    // Document management
    DOCUMENT_UPLOAD("Upload documents"),
    DOCUMENT_READ("Read documents"),
    DOCUMENT_DELETE("Delete documents"),

    // Billing
    BILLING_CREATE("Create invoices"),
    BILLING_READ("Read invoices"),
    BILLING_UPDATE("Update invoices"),
    BILLING_MARK_PAID("Mark invoices as paid"),

    // Calendar
    CALENDAR_CREATE("Create hearing/events"),
    CALENDAR_READ("Read calendar"),
    CALENDAR_UPDATE("Update events"),

    // Settings
    SETTINGS_READ("Read settings"),
    SETTINGS_UPDATE("Update settings");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}