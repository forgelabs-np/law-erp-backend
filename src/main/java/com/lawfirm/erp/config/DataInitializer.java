package com.lawfirm.erp.config;

import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import com.lawfirm.erp.common.enums.PlanTier;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.tenant.entity.TenantType;
import com.lawfirm.erp.tenant.repository.TenantTypeRepository;
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
    private final FirmRepository firmRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Initializing system data...");

        createSystemRoles();
        createTenantTypes();
        createSystemFirmForSuperAdmin();

        log.info("System data initialization completed.");
    }

    private void createSystemRoles() {
        // [roleName, roleCode, description, isSystem, applicableTo]
        Object[][] roles = {
                {"SUPER_ADMIN", "SUPER_ADMIN", "Full system access - controls everything", true, null},
                {"FIRM_ADMIN", "FIRM_ADMIN", "Manages law firm operations", true, UserType.FIRM_USER},
                {"ADVOCATE", "ADVOCATE", "Practicing lawyer", true, UserType.FIRM_USER},
                {"PARALEGAL", "PARALEGAL", "Support staff", true, UserType.FIRM_USER},
                {"CLIENT", "CLIENT", "Client of the firm", true, UserType.CLIENT}
        };

        for (Object[] roleData : roles) {
            String roleCode = (String) roleData[1];
            if (!roleRepository.existsByRoleCode(roleCode)) {
                Role role = new Role();
                role.setRoleName((String) roleData[0]);
                role.setRoleCode(roleCode);
                role.setDescription((String) roleData[2]);
                role.setIsSystem((Boolean) roleData[3]);
                role.setActive(true);
                if (roleData[4] != null) {
                    role.setApplicableTo((UserType) roleData[4]);
                }
                roleRepository.save(role);
                log.info("Created system role: {}", roleData[0]);
            }
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
            }
        }
    }

    private void createSystemFirmForSuperAdmin() {
        if (firmRepository.findByLawFirmCode("SYSTEM").isEmpty()) {
            Firm systemFirm = Firm.builder()
                    .lawFirmCode("SYSTEM")
                    .name("System Platform")
                    .firmType(FirmType.SOLO)
                    .status(FirmStatus.ACTIVE)
                    .planTier(PlanTier.ENTERPRISE)
                    .maxEmployees(1)
                    .build();
            firmRepository.save(systemFirm);
            log.info("Created system firm for SUPER_ADMIN");
        }
    }
}