//package com.lawfirm.erp.enums;
//import java.util.EnumSet;
//import java.util.Set;
//
//public class RolePermissionMapping {
//
//    public static Set<Permission> getPermissionsForRole(RoleType role) {
//        return switch (role) {
//            case SUPER_ADMIN -> EnumSet.allOf(Permission.class);
//
//            case TENANT_ADMIN -> EnumSet.of(
//                    Permission.USER_CREATE, Permission.USER_READ, Permission.USER_UPDATE, Permission.USER_DELETE,
//                    Permission.SETTINGS_READ, Permission.SETTINGS_UPDATE,
//                    Permission.BILLING_READ, Permission.BILLING_CREATE, Permission.BILLING_UPDATE,
//                    Permission.CASE_READ, Permission.CLIENT_READ, Permission.DOCUMENT_READ
//            );
//
//            case LAWYER -> EnumSet.of(
//                    Permission.CASE_CREATE, Permission.CASE_READ, Permission.CASE_UPDATE,
//                    Permission.CLIENT_CREATE, Permission.CLIENT_READ, Permission.CLIENT_UPDATE,
//                    Permission.DOCUMENT_UPLOAD, Permission.DOCUMENT_READ,
//                    Permission.BILLING_CREATE, Permission.BILLING_READ,
//                    Permission.CALENDAR_CREATE, Permission.CALENDAR_READ, Permission.CALENDAR_UPDATE
//            );
//
//            case CLIENT -> EnumSet.of(
//                    Permission.CASE_READ,
//                    Permission.DOCUMENT_UPLOAD, Permission.DOCUMENT_READ,
//                    Permission.BILLING_READ
//            );
//        };
//    }
//}
