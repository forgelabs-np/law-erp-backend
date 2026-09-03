package com.lawfirm.erp.common.enums;

public enum UserType {
    SUPER_ADMIN,   // Platform admin
    FIRM,          // Firm owner / admin
    FIRM_USER,     // Lawyers, paralegals, clerks (firm employees)
    CLIENT         // Law firm's customers
}