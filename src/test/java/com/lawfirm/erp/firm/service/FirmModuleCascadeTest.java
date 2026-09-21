package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.request.EnableModuleRequest;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirmModuleCascadeTest {

    @Mock private FirmModuleRepository firmModuleRepository;
    @Mock private FirmRepository firmRepository;
    @Mock private ModuleRepository moduleRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private AuditService auditService;

    private FirmModuleServiceImpl firmModuleService;

    private final UUID firmId = UUID.randomUUID();
    private Module parent;
    private Module child1;
    private Module child2;

    @BeforeEach
    void setUp() {
        firmModuleService = new FirmModuleServiceImpl(
                firmModuleRepository, firmRepository, moduleRepository, currentUserResolver, auditService);

        child1 = module("TESTCONFIG1", null);
        child2 = module("TESTCONFIG2", null);
        parent = module("TESTCONFIG", null);
        child1.setParent(parent);
        child2.setParent(parent);
        parent.setSubModules(List.of(child1, child2));

        when(firmRepository.findById(firmId)).thenReturn(Optional.of(Firm.builder().lawFirmCode("TEST01").build()));
        when(moduleRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(moduleRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
        when(firmModuleRepository.findByFirmIdAndModuleId(eq(firmId), any(UUID.class)))
                .thenReturn(Optional.empty());
        when(firmModuleRepository.save(any(FirmModule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Module module(String code, Module parent) {
        Module m = Module.builder().code(code).name(code).parent(parent).level(parent == null ? 0 : 1).build();
        m.setId(UUID.randomUUID());
        m.setActive(true);
        return m;
    }

    private EnableModuleRequest request(UUID moduleId, boolean enabled) {
        EnableModuleRequest req = new EnableModuleRequest();
        req.setModuleId(moduleId);
        req.setIsEnabled(enabled);
        return req;
    }

    @Test
    @DisplayName("Enabling a module also enables its sub-modules")
    void enablingParentEnablesSubModules() {
        firmModuleService.enableModuleForFirm(firmId, request(parent.getId(), true));

        ArgumentCaptor<FirmModule> captor = ArgumentCaptor.forClass(FirmModule.class);
        verify(firmModuleRepository, times(3)).save(captor.capture());

        List<String> savedCodes = captor.getAllValues().stream()
                .map(fm -> fm.getModule().getCode())
                .toList();
        assertEquals(List.of("TESTCONFIG", "TESTCONFIG1", "TESTCONFIG2"), savedCodes);
        assertTrue(captor.getAllValues().stream().allMatch(fm -> Boolean.TRUE.equals(fm.getIsEnabled())));
        assertTrue(captor.getAllValues().stream().allMatch(fm -> fm.getEnabledAt() != null));
    }

    @Test
    @DisplayName("Disabling a module also disables its sub-modules")
    void disablingParentDisablesSubModules() {
        FirmModule existingParent = FirmModule.builder().module(parent).isEnabled(true).build();
        when(firmModuleRepository.findByFirmIdAndModuleId(firmId, parent.getId()))
                .thenReturn(Optional.of(existingParent));

        firmModuleService.enableModuleForFirm(firmId, request(parent.getId(), false));

        ArgumentCaptor<FirmModule> captor = ArgumentCaptor.forClass(FirmModule.class);
        verify(firmModuleRepository, times(3)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(fm -> Boolean.FALSE.equals(fm.getIsEnabled())));
    }

    @Test
    @DisplayName("Enabling a leaf sub-module does not touch its parent")
    void enablingLeafOnlyTouchesItself() {
        firmModuleService.enableModuleForFirm(firmId, request(child1.getId(), true));

        ArgumentCaptor<FirmModule> captor = ArgumentCaptor.forClass(FirmModule.class);
        verify(firmModuleRepository, times(1)).save(captor.capture());
        assertEquals("TESTCONFIG1", captor.getValue().getModule().getCode());
    }
}
