package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.enums.CaseType;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Year;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseNumberGeneratorTest {

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final String FIRM_CODE = "APX";

    @Mock private CaseRepository caseRepository;

    private CaseNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CaseNumberGenerator(caseRepository);
        FirmContextHolder.set(FIRM_ID, FIRM_CODE);
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    @Test
    @DisplayName("Generates first case number when no cases exist")
    void firstCaseNumber() {
        when(caseRepository.findMaxCaseNumberByPattern(eq(FIRM_ID), anyString(), any(PageRequest.class)))
                .thenReturn(List.of());

        String number = generator.generate(FIRM_CODE, CaseType.CIVIL);

        int year = Year.now().getValue();
        assertEquals(String.format("APX-CIV-%d-00001", year), number);
    }

    @Test
    @DisplayName("Increments sequence from existing case number")
    void incrementsSequence() {
        int year = Year.now().getValue();
        when(caseRepository.findMaxCaseNumberByPattern(eq(FIRM_ID), anyString(), any(PageRequest.class)))
                .thenReturn(List.of(String.format("APX-CIV-%d-00005", year)));

        String number = generator.generate(FIRM_CODE, CaseType.CIVIL);

        assertEquals(String.format("APX-CIV-%d-00006", year), number);
    }

    @Test
    @DisplayName("Criminal cases get CRM prefix")
    void criminalPrefix() {
        int year = Year.now().getValue();
        when(caseRepository.findMaxCaseNumberByPattern(eq(FIRM_ID), anyString(), any(PageRequest.class)))
                .thenReturn(List.of());

        String number = generator.generate(FIRM_CODE, CaseType.CRIMINAL);

        assertEquals(String.format("APX-CRM-%d-00001", year), number);
    }
}
