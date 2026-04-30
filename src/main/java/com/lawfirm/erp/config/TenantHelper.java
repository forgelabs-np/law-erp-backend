//package com.lawfirm.erp.config;
//
//import com.lawfirm.erp.entity.User;
//
//@Deprecated
//public class TenantHelper {
//
//    private static final String SYSTEM_SUBDOMAIN = "SYSTEM";
//    private static final String SYSTEM_TYPE = "SYSTEM";
//
//    public static boolean isSystemUser(User user) {
//        return user.getTenant() == null;
//    }
//
//    public static String getTenantSubdomain(User user) {
//        if (user.getTenant() == null) {
//            return SYSTEM_SUBDOMAIN;
//        }
//        return user.getTenant().getSubdomain();
//    }
//
//    public static String getTenantType(User user) {
//        if (user.getTenant() == null) {
//            return SYSTEM_TYPE;
//        }
//        return user.getTenant().getTenantType().name();
//    }
//
//    public static Long getTenantId(User user) {
//        if (user.getTenant() == null) {
//            return null;
//        }
//        return user.getTenant().getId();
//    }
//}