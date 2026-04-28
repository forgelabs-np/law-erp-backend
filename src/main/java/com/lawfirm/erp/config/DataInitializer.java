//package com.lawfirm.erp.config;
//
//import com.lawfirm.erp.entity.Role;
//import com.lawfirm.erp.enums.RoleType;
//import com.lawfirm.erp.repository.RoleRepository;
//import jakarta.annotation.PostConstruct;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.transaction.annotation.Transactional;
//
//@Configuration
//@RequiredArgsConstructor
//@Slf4j
//public class DataInitializer {
//
//    private final RoleRepository roleRepository;
//
//    @PostConstruct
//    @Transactional
//    public void init() {
//        log.info("Starting role initialization...");
//        initRoles();
//        log.info("Role initialization completed.");
//    }
//
//    private void initRoles() {
//        log.info("Initializing roles...");
//
//        for (RoleType roleType : RoleType.values()) {
//            if (!roleRepository.existsByName(roleType)) {
//                Role role = new Role();
//                role.setName(roleType);
//                role.setDescription(roleType.getDescription());
//                role.setIsActive(true);
//                roleRepository.save(role);
//                log.info("Created role: {}", roleType.name());
//            } else {
//                log.debug("Role already exists: {}", roleType.name());
//            }
//        }
//
//        log.info("Roles initialization completed. Total roles: {}", roleRepository.count());
//    }
//}