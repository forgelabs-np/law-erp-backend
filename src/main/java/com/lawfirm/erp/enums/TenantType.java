package com.lawfirm.erp.enums;

@Deprecated
public enum TenantType {
    SOLO("Solo Practitioner", "Single lawyer practice"),
    FIRM("Law Firm", "Multiple lawyers, support staff");

    private final String displayName;
    private final String description;

    TenantType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}