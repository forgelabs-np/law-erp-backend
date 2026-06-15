//package com.lawfirm.erp.security;
//
//import jakarta.persistence.EntityManager;
//import jakarta.persistence.PersistenceContext;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import org.hibernate.Session;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.HandlerInterceptor;
//
//import java.util.UUID;
////
////@Component
////@RequiredArgsConstructor
//public class HibernateFilterEnabler implements HandlerInterceptor {
//
////    @PersistenceContext
////    private final EntityManager entityManager;
////
////    @Override
////    public boolean preHandle(HttpServletRequest request,
////                             HttpServletResponse response,
////                             Object handler) {
////        UUID firmId = FirmContextHolder.getFirmId();
////        if (firmId != null) {
////            entityManager.unwrap(Session.class)
////                    .enableFilter("tenantFilter")
////                    .setParameter("firmId", firmId);
////        }
////        return true;
////    }
//}