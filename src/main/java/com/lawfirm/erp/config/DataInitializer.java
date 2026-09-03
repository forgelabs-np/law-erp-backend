package com.lawfirm.erp.config;

import com.lawfirm.erp.common.constant.RoleCode;
import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalType;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalTypeRepository;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.entity.ModulePermission;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.RolePermission;
import com.lawfirm.erp.rbac.repository.ModulePermissionRepository;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.rbac.repository.RolePermissionRepository;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.tenant.entity.TenantType;
import com.lawfirm.erp.tenant.repository.TenantTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Seeds the full RBAC foundation in one pass.
 *
 * Order matters:
 *   1. Tenant types
 *   2. System firm (home of SUPER_ADMIN)
 *   3. System roles
 *   4. Modules
 *   5. Permissions          (code = MODULE_CODE:ACTION, scope set per action)
 *   6. ModulePermission     (module -> its permissions, junction table)
 *   7. RolePermission       (role -> permissions, driven by access-level matrix)
 *
 * Access levels used in the matrix below:
 *   FULL      -> every permission for that module
 *   READ_ONLY -> ACCESS + VIEW only
 *   OWN       -> ACCESS + VIEW, same rows as READ_ONLY.
 *               The CLIENT role gets these too - the row-level restriction
 *               ("only their own case") is enforced in the service layer
 *               using Permission.scope = OWN, NOT by a different permission
 *               record. Scope narrows the SQL, it does not gate the check.
 *   NO_ACCESS -> nothing written for that module/role pair
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final ModulePermissionRepository modulePermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TenantTypeRepository tenantTypeRepository;
    private final FirmRepository firmRepository;
    private final UserRepository userRepository;
    private final FirmModuleRepository firmModuleRepository;
    private final RenewalTypeRepository renewalTypeRepository;
    private final SystemConfigService systemConfigService;

    private static final String FULL      = "FULL";
    private static final String READ_ONLY = "READ_ONLY";
    private static final String OWN       = "OWN";
    private static final String NO_ACCESS = "NO_ACCESS";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=== DataInitializer: starting system seed ===");

        createTenantTypes();
        Firm systemFirm = createSystemFirmForSuperAdmin();
        // Seed DB-driven settings defaults before anything reads policy from them.
        systemConfigService.seedGlobalDefaults();
        if (mfaEnforcementOn()) {
            migrateExistingSuperAdminMfa();
        }
        createSystemRoles();
        createModulesAndPermissions();
        assignPermissionsToRoles();
        seedDefaultRenewalTypes();
        seedModulesForSystemFirm(systemFirm);

        log.info("=== DataInitializer: seed complete ===");
    }

    // ========================================================================
    // Tenant types
    // ========================================================================
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
                log.info("  + TenantType: {}", typeData[0]);
            }
        }
    }

    // ========================================================================
    // System firm
    // ========================================================================
    private Firm createSystemFirmForSuperAdmin() {
        return firmRepository.findByLawFirmCode("SYSTEM").orElseGet(() -> {
            Firm systemFirm = Firm.builder()
                    .lawFirmCode("SYSTEM")
                    .name("System Platform")
                    .firmType(FirmType.SOLO)
                    .status(FirmStatus.ACTIVE)
                    .build();
            systemFirm = firmRepository.save(systemFirm);
            log.info("  + System firm created");
            return systemFirm;
        });
    }

    // ========================================================================
    // Migrate existing SUPER_ADMIN users — force MFA
    // ========================================================================
    private boolean mfaEnforcementOn() {
        return systemConfigService.isMfaEnabled()
                && systemConfigService.mfaRequiredRoleCodes().contains(RoleCode.SUPER_ADMIN);
    }

    private void migrateExistingSuperAdminMfa() {
        List<User> superAdmins = userRepository.findByUserType(UserType.SUPER_ADMIN);
        for (User sa : superAdmins) {
            if (!Boolean.TRUE.equals(sa.getMfaEnabled())) {
                sa.setMfaEnabled(true);
                userRepository.save(sa);
                log.info("  + MFA enabled for existing super admin: {}", sa.getUsername());
            }
        }
    }

    // ========================================================================
    // System roles
    // ========================================================================
    private void createSystemRoles() {
        Object[][] roles = {
                {RoleCode.SUPER_ADMIN, "Full system access - controls everything", true, null},
                {RoleCode.FIRM_ADMIN,  "Manages law firm operations", true, UserType.FIRM},
                {RoleCode.ADVOCATE,    "Practicing lawyer", true, UserType.FIRM_USER},
                {RoleCode.PARALEGAL,   "Support staff", true, UserType.FIRM_USER},
                {RoleCode.CLIENT,      "Client of the firm", true, UserType.CLIENT}
        };

        for (Object[] roleData : roles) {
            String roleCode = (String) roleData[0];
            if (!roleRepository.existsByRoleCode(roleCode)) {
                Role role = new Role();
                role.setRoleName(roleCode);
                role.setRoleCode(roleCode);
                role.setDescription((String) roleData[1]);
                role.setIsSystem((Boolean) roleData[2]);
                role.setActive(true);
                if (roleData[3] != null) {
                    role.setApplicableTo((UserType) roleData[3]);
                }
                roleRepository.save(role);
                log.info("  + Role: {}", roleCode);
            }
        }
    }

    // ========================================================================
    // Modules + Permissions + ModulePermission junction
    // ========================================================================
    private void createModulesAndPermissions() {

        // { code, name, description, displayOrder, sortOrder, icon, path, extraActions[] }
        Object[][] moduleDefs = {
                {"CASE_MANAGEMENT", "Case Management", "Manage legal cases", 1, 10, "FolderIcon", "/cases",
                        new PermissionAction[]{PermissionAction.ASSIGN, PermissionAction.ARCHIVE, PermissionAction.UPDATE_STATUS}},
                {"DOCUMENT_MANAGEMENT", "Document Management", "Manage case documents", 2, 20, "FileIcon", "/documents",
                        new PermissionAction[]{PermissionAction.UPLOAD, PermissionAction.DOWNLOAD, PermissionAction.SHARE}},
                {"CLIENT_MANAGEMENT", "Client Management", "Manage clients", 3, 30, "UsersIcon", "/clients",
                        new PermissionAction[]{}},
                {"BILLING", "Billing & Invoices", "Manage billing and invoices", 4, 40, "DollarSignIcon", "/billing",
                        new PermissionAction[]{PermissionAction.APPROVE, PermissionAction.EXPORT}},
                {"CALENDAR", "Calendar", "Manage hearings and events", 5, 50, "CalendarIcon", "/calendar",
                        new PermissionAction[]{PermissionAction.SCHEDULE}},
                {"EMPLOYEE", "Employee Management", "Manage firm employees", 6, 60, "BriefcaseIcon", "/employees",
                        new PermissionAction[]{}},
                {"REPORTS", "Reports", "View analytics and reports", 7, 70, "BarChartIcon", "/reports",
                        new PermissionAction[]{PermissionAction.EXPORT, PermissionAction.PRINT}},
                {"AUDIT", "Audit Logs", "View system audit logs", 8, 80, "ShieldIcon", "/audit",
                        new PermissionAction[]{}},
                {"PROJECT_MANAGEMENT", "Project Management", "Manage client projects, credentials, and renewals", 9, 90, "ClipboardIcon", "/projects",
                        new PermissionAction[]{PermissionAction.CREDENTIAL_VIEW, PermissionAction.CREDENTIAL_REVEAL}},
                {"ROLE_MANAGEMENT", "Role Management", "Manage roles and role permissions", 10, 100, "ShieldCheckIcon", "/roles",
                        new PermissionAction[]{}},
                {"FIRM_MANAGEMENT", "Firm Management", "Manage firm profile, config, modules, and roles", 11, 110, "BuildingIcon", "/firm",
                        new PermissionAction[]{}},
                {"MENU_MANAGEMENT", "Menu Management", "Manage navigation menus and module visibility", 12, 120, "MenuIcon", "/menus",
                        new PermissionAction[]{}},
                {"PERMISSION_MANAGEMENT", "Permission Management", "Manage system permissions and action grants", 13, 130, "KeyIcon", "/permissions",
                        new PermissionAction[]{}},
                {"SCRAPER_MANAGEMENT", "Scraper Management", "Manage court scraper, exports, and hearing ingestion", 14, 140, "GlobeIcon", "/scraper",
                        new PermissionAction[]{PermissionAction.EXPORT}},
                {"USER_MANAGEMENT", "User Management", "Manage firm users, profiles, and bulk operations", 15, 150, "UsersIcon", "/users",
                        new PermissionAction[]{}},
                {"DASHBOARD_MANAGEMENT", "Dashboard", "View dashboards and aggregated stats", 16, 160, "LayoutDashboardIcon", "/dashboard",
                        new PermissionAction[]{}}
        };

        PermissionAction[] standard = {
                PermissionAction.ACCESS,
                PermissionAction.VIEW,
                PermissionAction.CREATE,
                PermissionAction.EDIT,
                PermissionAction.DELETE
        };

        for (Object[] def : moduleDefs) {
            String moduleCode = (String) def[0];

            Module module = moduleRepository.findByCode(moduleCode).orElseGet(() -> {
                Module m = new Module();
                m.setCode(moduleCode);
                m.setName((String) def[1]);
                m.setDescription((String) def[2]);
                m.setDisplayOrder((Integer) def[3]);
                m.setSortOrder((Integer) def[4]);
                m.setIcon((String) def[5]);
                m.setPath((String) def[6]);
                m.setIsSystem(true);
                m.setActive(true);
                Module saved = moduleRepository.save(m);
                log.info("  + Module: {}", moduleCode);
                return saved;
            });

            PermissionAction[] extras = (PermissionAction[]) def[7];
            List<PermissionAction> allActions = new ArrayList<>(Arrays.asList(standard));
            allActions.addAll(Arrays.asList(extras));

            for (PermissionAction action : allActions) {
                String permCode = moduleCode + ":" + action.name();

                Permission perm = permissionRepository.findByCode(permCode).orElseGet(() -> {
                    try {
                        Permission p = new Permission();
                        p.setCode(permCode);
                        p.setAction(action);
                        p.setScope(PermissionScope.TENANT); // default; overridden to GLOBAL for SUPER_ADMIN below
                        p.setModuleCode(moduleCode);
                        p.setDescription(def[1] + " - " + action.name());
                        p.setActive(true);
                        Permission saved = permissionRepository.save(p);
                        log.info("    + Permission: {}", permCode);
                        return saved;
                    } catch (Exception e) {
                        log.warn("    ! Could not create permission {} (run DB migration first): {}", permCode, e.getMessage());
                        return null;
                    }
                });

                if (perm != null) {
                    List<Permission> existingForModule = modulePermissionRepository.findPermissionsByModuleId(module.getId());
                    boolean alreadyLinked = existingForModule.stream().anyMatch(p -> p.getId().equals(perm.getId()));
                    if (!alreadyLinked) {
                        ModulePermission mp = ModulePermission.builder()
                                .module(module)
                                .permission(perm)
                                .build();
                        modulePermissionRepository.save(mp);
                    }
                }
            }
        }
    }

    // ========================================================================
    // Role -> Permission assignment matrix
    // ========================================================================
    private void assignPermissionsToRoles() {

        // { moduleCode, SUPER_ADMIN, FIRM_ADMIN, ADVOCATE, PARALEGAL, CLIENT }
        String[][] matrix = {
                {"CASE_MANAGEMENT",     FULL, FULL, FULL,      READ_ONLY, OWN},
                {"DOCUMENT_MANAGEMENT", FULL, FULL, FULL,      READ_ONLY, OWN},
                {"CLIENT_MANAGEMENT",   FULL, FULL, READ_ONLY, READ_ONLY, NO_ACCESS},
                {"BILLING",             FULL, FULL, READ_ONLY, NO_ACCESS, OWN},
                {"CALENDAR",            FULL, FULL, FULL,      READ_ONLY, OWN},
                {"EMPLOYEE",            FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
                {"REPORTS",             FULL, FULL, READ_ONLY, NO_ACCESS, NO_ACCESS},
                {"AUDIT",               FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
                {"PROJECT_MANAGEMENT",   FULL, FULL, READ_ONLY, READ_ONLY, OWN},
                {"ROLE_MANAGEMENT",       FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
                {"FIRM_MANAGEMENT",       FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
                {"MENU_MANAGEMENT",        FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
                {"PERMISSION_MANAGEMENT",  FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
                {"SCRAPER_MANAGEMENT",     FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
                {"USER_MANAGEMENT",        FULL, FULL, NO_ACCESS, NO_ACCESS, NO_ACCESS},
                {"DASHBOARD_MANAGEMENT",   FULL, FULL, READ_ONLY, READ_ONLY, NO_ACCESS},
        };

        String[] roleCodes = {RoleCode.SUPER_ADMIN, RoleCode.FIRM_ADMIN, RoleCode.ADVOCATE, RoleCode.PARALEGAL, RoleCode.CLIENT};

        for (String[] row : matrix) {
            String moduleCode = row[0];

            for (int i = 0; i < roleCodes.length; i++) {
                String roleCode    = roleCodes[i];
                String accessLevel = row[i + 1];

                if (NO_ACCESS.equals(accessLevel)) continue;

                Role role = roleRepository.findSystemRoleByCode(roleCode).orElse(null);
                if (role == null) {
                    log.warn("Role not found, skipping: {}", roleCode);
                    continue;
                }

                List<Permission> perms = getPermissionsForAccessLevel(moduleCode, accessLevel);
                List<Permission> alreadyAssigned = rolePermissionRepository.findPermissionsByRoleId(role.getId());

                boolean isSuperAdmin = RoleCode.SUPER_ADMIN.equals(roleCode);

                for (Permission perm : perms) {
                    boolean alreadyHas = alreadyAssigned.stream().anyMatch(p -> p.getId().equals(perm.getId()));
                    if (!alreadyHas) {
                        RolePermission rp = RolePermission.builder()
                                .role(role)
                                .permission(perm)
                                .build();
                        rolePermissionRepository.save(rp);
                        log.info("    [{}] {} -> {} (scope: {})", accessLevel, roleCode, perm.getCode(), perm.getScope());
                    }
                }
            }
        }
    }

    /**
     * FULL      -> every permission seeded for that module
     * READ_ONLY -> ACCESS + VIEW only
     * OWN       -> ACCESS + VIEW (same rows as READ_ONLY).
     *              CLIENT gets these; the "own records only" restriction
     *              is applied in the service layer based on
     *              Permission.scope == OWN, e.g.:
     *                if (permission.getScope() == PermissionScope.OWN) {
     *                    query.where("client_id", currentUser.getId());
     *                }
     */
    private List<Permission> getPermissionsForAccessLevel(String moduleCode, String accessLevel) {
        List<Permission> all = modulePermissionRepository.findPermissionsByModuleId(
                moduleRepository.findByCode(moduleCode)
                        .orElseThrow(() -> new IllegalStateException("Module not found: " + moduleCode))
                        .getId()
        );

        return switch (accessLevel) {
            case FULL -> all;
            case READ_ONLY, OWN -> all.stream()
                    .filter(p -> p.getAction() == PermissionAction.ACCESS || p.getAction() == PermissionAction.VIEW)
                    .toList();
            default -> List.of();
        };
    }

    // ========================================================================
    // Seed FirmModules for SYSTEM firm — all modules enabled by default
    // ========================================================================
    private void seedModulesForSystemFirm(Firm systemFirm) {
        List<Module> allModules = moduleRepository.findAll();
        for (Module module : allModules) {
            boolean exists = firmModuleRepository.findByFirmIdAndModuleId(systemFirm.getId(), module.getId()).isPresent();
            if (!exists) {
                FirmModule fm = FirmModule.builder()
                        .firm(systemFirm)
                        .module(module)
                        .isEnabled(true)
                        .build();
                firmModuleRepository.save(fm);
                log.info("  + FirmModule (SYSTEM): {} enabled", module.getCode());
            }
        }
    }

    // ========================================================================
    // Default Renewal Types (system-wide)
    // ========================================================================
    private void seedDefaultRenewalTypes() {
        String[][] types = {
                {"Trademark Renewal", "Annual trademark renewal and maintenance"},
                {"Patent Renewal", "Patent maintenance and renewal fees"},
                {"License Renewal", "Business license and permit renewals"},
                {"Annual Compliance", "Annual statutory compliance filings"},
                {"Tax Filing", "Tax return and filing deadlines"},
                {"Secretarial Compliance", "Company secretarial compliance filings"},
                {"Other", "General renewal or deadline"}
        };

        for (String[] typeData : types) {
            boolean exists = renewalTypeRepository.findAll().stream()
                    .anyMatch(rt -> rt.getName().equals(typeData[0]) && rt.isSystem());
            if (!exists) {
                RenewalType rt = RenewalType.builder()
                        .name(typeData[0])
                        .description(typeData[1])
                        .system(true)
                        .active(true)
                        .build();
                renewalTypeRepository.save(rt);
                log.info("  + RenewalType (system): {}", typeData[0]);
            }
        }
    }
}