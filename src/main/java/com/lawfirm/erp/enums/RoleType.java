package com.lawfirm.erp.enums;

public enum RoleType {
    SUPER_ADMIN("Super Administrator", "Full system access, manage all tenants"),
    TENANT_ADMIN("Tenant Administrator", "Manage firm/solo settings, users, billing"),
    LAWYER("Lawyer", "Manage cases, clients, documents, calendar"),
    CLIENT("Client", "View own matters, upload documents, communicate");

    private final String displayName;
    private final String description;

    RoleType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}