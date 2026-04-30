// config/DataInitializer.java
package com.lawfirm.erp.config;

import com.lawfirm.erp.entity.Role;
import com.lawfirm.erp.entity.TenantType;
import com.lawfirm.erp.repository.RoleRepository;
import com.lawfirm.erp.repository.TenantTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final TenantTypeRepository tenantTypeRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Initializing system data...");

        createSuperAdminRole();

        createTenantTypes();

        log.info("System data initialization completed.");
    }

    private void createSuperAdminRole() {
        String[] superAdminData = {"SUPER_ADMIN", "SUPER_ADMIN", "Full system access - controls everything"};

        if (!roleRepository.existsByRoleName(superAdminData[0])) {
            Role role = new Role();
            role.setRoleName(superAdminData[0]);
            role.setRoleCode(superAdminData[1]);
            role.setDescription(superAdminData[2]);
            role.setIsSystem(true);
            role.setActive(true);
            roleRepository.save(role);
            log.info("Created SUPER_ADMIN role");
        } else {
            log.info("SUPER_ADMIN role already exists, skipping creation");
        }
    }

    private void createTenantTypes() {
        String[][] tenantTypes = {
                {"SOLO", "Solo Practitioner", "Single lawyer practice"},
                {"LAW_FIRM", "Law Firm", "Multiple lawyers, support staff"}
        };

        for (String[] typeData : tenantTypes) {
            if (!tenantTypeRepository.existsByCode(typeData[0])) {
                TenantType tenantType = new TenantType();
                tenantType.setName(typeData[1]);
                tenantType.setCode(typeData[0]);
                tenantType.setDescription(typeData[2]);
                tenantType.setActive(true);
                tenantTypeRepository.save(tenantType);
                log.info("Created tenant type: {}", typeData[0]);
            } else {
                log.info("Tenant type {} already exists, skipping creation", typeData[0]);
            }
        }
    }
}