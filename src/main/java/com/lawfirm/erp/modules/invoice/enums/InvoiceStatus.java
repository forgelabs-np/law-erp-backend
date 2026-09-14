package com.lawfirm.erp.modules.invoice.enums;

public enum InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    OVERDUE,
    CANCELED;

    public boolean canTransitionTo(InvoiceStatus target) {
        return switch (this) {
            case DRAFT    -> target == SENT || target == CANCELED;
            case SENT     -> target == PAID || target == OVERDUE || target == CANCELED;
            case OVERDUE  -> target == PAID || target == CANCELED;
            case PAID     -> false; // terminal
            case CANCELED -> false; // terminal
        };
    }
}
