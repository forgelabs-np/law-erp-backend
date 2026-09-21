package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.rbac.entity.Module;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ModuleAccessResolverTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    private Module module(String code, Module parent) {
        Module m = Module.builder().code(code).name(code).parent(parent).level(parent == null ? 0 : 1).build();
        m.setId(UUID.randomUUID());
        m.setActive(true);
        return m;
    }

    private FirmModule row(Module m, boolean enabled) {
        return FirmModule.builder().module(m).isEnabled(enabled).build();
    }

    private Map<UUID, FirmModule> rows(List<FirmModule> list) {
        return ModuleAccessResolver.indexByModuleId(list);
    }

    @Test
    @DisplayName("An explicit row decides access for that module")
    void explicitRowDecides() {
        Module parent = module("TESTCONFIG", null);

        assertTrue(ModuleAccessResolver.isEnabled(parent, rows(List.of(row(parent, true)))));
        assertFalse(ModuleAccessResolver.isEnabled(parent, rows(List.of(row(parent, false)))));
    }

    @Test
    @DisplayName("A sub-module without its own row inherits the enabled parent")
    void subModuleInheritsParent() {
        Module parent = module("TESTCONFIG", null);
        Module child = module("TESTCONFIG1", parent);

        assertTrue(ModuleAccessResolver.isEnabled(child, rows(List.of(row(parent, true)))));
    }

    @Test
    @DisplayName("A sub-module is denied when the parent is disabled")
    void subModuleDeniedWhenParentDisabled() {
        Module parent = module("TESTCONFIG", null);
        Module child = module("TESTCONFIG1", parent);

        assertFalse(ModuleAccessResolver.isEnabled(child, rows(List.of(row(parent, false)))));
    }

    @Test
    @DisplayName("An explicit child row wins over its enabled parent")
    void explicitChildWinsOverParent() {
        Module parent = module("TESTCONFIG", null);
        Module child = module("TESTCONFIG1", parent);

        assertFalse(ModuleAccessResolver.isEnabled(child,
                rows(List.of(row(parent, true), row(child, false)))));
    }

    @Test
    @DisplayName("Inheritance reaches through several levels")
    void inheritanceIsRecursive() {
        Module parent = module("TESTCONFIG", null);
        Module child = module("TESTCONFIG1", parent);
        Module grandChild = module("TESTCONFIG1A", child);

        assertTrue(ModuleAccessResolver.isEnabled(grandChild, rows(List.of(row(parent, true)))));
    }

    @Test
    @DisplayName("No row anywhere means no access")
    void noRowMeansNoAccess() {
        Module orphan = module("TESTCONFIG", null);

        assertFalse(ModuleAccessResolver.isEnabled(orphan, rows(List.of())));
    }

    @Test
    @DisplayName("An expired row counts as disabled, including for inheriting children")
    void expiredRowCountsAsDisabled() {
        Module parent = module("TESTCONFIG", null);
        Module child = module("TESTCONFIG1", parent);
        FirmModule expired = row(parent, true);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertFalse(ModuleAccessResolver.isEnabled(parent, rows(List.of(expired))));
        assertFalse(ModuleAccessResolver.isEnabled(child, rows(List.of(expired))));
    }
}
