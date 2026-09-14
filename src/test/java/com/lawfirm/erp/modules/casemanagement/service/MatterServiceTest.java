package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.AddCourtCaseRequest;
import com.lawfirm.erp.modules.casemanagement.entity.CourtCase;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.*;
import com.lawfirm.erp.modules.casemanagement.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatterServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock private MatterRepository matterRepository;
    @Mock private CourtCaseRepository courtCaseRepository;
    @Mock private CourtEventRepository courtEventRepository;
    @Mock private MatterPartyRepository matterPartyRepository;
    @Mock private CourtCaseRoleRepository courtCaseRoleRepository;
    @Mock private MatterTimelineRepository matterTimelineRepository;
    @Mock private MatterNumberGenerator matterNumberGenerator;
    @Mock private CourtCaseRefGenerator courtCaseRefGenerator;
    @Mock private AuditService auditService;

    @InjectMocks
    private MatterServiceImpl matterService;

    @BeforeEach
    void setUp() {
        FirmContextHolder.set(FIRM_ID, "APX");
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private Matter matter() {
        Matter m = new Matter();
        m.setId(UUID.randomUUID());
        m.setFirmId(FIRM_ID);
        m.setMatterNumber("APX-MAT-2026-00001");
        m.setMatterType(MatterType.CIVIL);
        m.setStatus(MatterStatus.ACTIVE);
        return m;
    }

    private CourtCase courtCase(UUID id, CourtCaseStage stage, CourtCaseStatus status) {
        CourtCase cc = new CourtCase();
        cc.setId(id);
        cc.setFirmId(FIRM_ID);
        cc.setMatterId(UUID.randomUUID());
        cc.setCourtLevel(CourtLevel.DISTRICT);
        cc.setRelationType(RelationType.ORIGINAL);
        cc.setOurCourtCaseRef("APX-MAT-2026-00001-DC1");
        cc.setStage(stage);
        cc.setStatus(status);
        return cc;
    }

    private void stubChain(Matter m, CourtCase parent) {
        when(matterRepository.findByMatterNumberAndFirmId(m.getMatterNumber(), FIRM_ID))
                .thenReturn(Optional.of(m));
        when(courtCaseRepository.findByIdAndFirmId(parent.getId(), FIRM_ID))
                .thenReturn(Optional.of(parent));
        when(courtCaseRefGenerator.generate(anyString(), any(), any()))
                .thenReturn("APX-MAT-2026-00001-SC1");
        when(courtCaseRepository.save(any(CourtCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(matterRepository.save(any(Matter.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AddCourtCaseRequest request(RelationType relation, UUID parentId, CourtLevel level) {
        AddCourtCaseRequest req = new AddCourtCaseRequest();
        req.setRelationType(relation);
        req.setCourtLevel(level);
        req.setCourtName("Supreme Court");
        req.setParentCourtCaseId(parentId);
        return req;
    }

    @Test
    @DisplayName("APPEAL cannot attach to a closed parent")
    void appealOnClosedParentThrows() {
        Matter m = matter();
        CourtCase parent = courtCase(UUID.randomUUID(), CourtCaseStage.CLOSED, CourtCaseStatus.CLOSED);
        m.setCurrentCourtCaseId(parent.getId());
        stubChain(m, parent);

        AddCourtCaseRequest req = request(RelationType.APPEAL, parent.getId(), CourtLevel.HIGH);

        assertThrows(BusinessRuleException.class,
                () -> matterService.addCourtCase(m.getMatterNumber(), req));
    }

    @Test
    @DisplayName("REVIEW may attach to a closed parent and leaves it untouched")
    void reviewOnClosedParentAllowedAndParentUnchanged() {
        Matter m = matter();
        CourtCase parent = courtCase(UUID.randomUUID(), CourtCaseStage.CLOSED, CourtCaseStatus.CLOSED);
        m.setCurrentCourtCaseId(parent.getId());
        stubChain(m, parent);

        AddCourtCaseRequest req = request(RelationType.REVIEW, parent.getId(), CourtLevel.SUPREME);

        assertDoesNotThrow(() -> matterService.addCourtCase(m.getMatterNumber(), req));

        // The closed parent stays exactly as it was — REVIEW does not mark it APPEALED.
        assertEquals(CourtCaseStage.CLOSED, parent.getStage());
        assertEquals(CourtCaseStatus.CLOSED, parent.getStatus());
        verify(courtCaseRepository, never()).save(parent);
    }

    @Test
    @DisplayName("REVIEW does not mark an open parent APPEALED")
    void reviewDoesNotMarkParentAppealed() {
        Matter m = matter();
        CourtCase parent = courtCase(UUID.randomUUID(), CourtCaseStage.JUDGMENT_DELIVERED, CourtCaseStatus.DECIDED);
        m.setCurrentCourtCaseId(parent.getId());
        stubChain(m, parent);

        AddCourtCaseRequest req = request(RelationType.REVIEW, parent.getId(), CourtLevel.SUPREME);

        matterService.addCourtCase(m.getMatterNumber(), req);

        assertEquals(CourtCaseStage.JUDGMENT_DELIVERED, parent.getStage());
        assertEquals(CourtCaseStatus.DECIDED, parent.getStatus());
        verify(courtCaseRepository, never()).save(parent);
    }

    @Test
    @DisplayName("APPEAL marks the parent APPEALED (superseded)")
    void appealMarksParentAppealed() {
        Matter m = matter();
        CourtCase parent = courtCase(UUID.randomUUID(), CourtCaseStage.JUDGMENT_DELIVERED, CourtCaseStatus.DECIDED);
        m.setCurrentCourtCaseId(parent.getId());
        stubChain(m, parent);

        AddCourtCaseRequest req = request(RelationType.APPEAL, parent.getId(), CourtLevel.HIGH);

        matterService.addCourtCase(m.getMatterNumber(), req);

        assertEquals(CourtCaseStage.APPEALED, parent.getStage());
        assertEquals(CourtCaseStatus.APPEALED, parent.getStatus());
        verify(courtCaseRepository).save(parent);
    }
}
