package com.lawfirm.erp.common.enums;

public enum FirmType {
    SOLO("Solo Practitioner", "Single lawyer practice"),
    FIRM("Law Firm", "Multiple lawyers, support staff");

    private final String displayName;
    private final String description;

    FirmType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}