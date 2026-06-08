package com.lawfirm.erp.common.enums;

public enum PlanTier {
    BASIC(5, "Up to 5 employees"),
    PROFESSIONAL(20, "Up to 20 employees"),
    ENTERPRISE(100, "Unlimited employees");

    private final int maxEmployees;
    private final String description;

    PlanTier(int maxEmployees, String description) {
        this.maxEmployees = maxEmployees;
        this.description = description;
    }

    public int getMaxEmployees() { return maxEmployees; }
    public String getDescription() { return description; }
}