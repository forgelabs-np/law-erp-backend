package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.customer.entity.CustomerProfile;
import com.lawfirm.erp.customer.repository.CustomerProfileRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.casemanagement.dto.response.PartyMatchResult;
import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.entity.CaseParty;
import com.lawfirm.erp.modules.casemanagement.repository.CasePartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartyMatchServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private CasePartyRepository casePartyRepository;
    @Mock private CaseRepository caseRepository;

    private PartyMatchService partyMatchService;

    private User makeUser(String name, String phone, String email) {
        User u = new User();
        u.setFullName(name);
        u.setMobileNo(phone);
        u.setEmail(email);
        return u;
    }

    private CustomerProfile makeClient(User user) {
        CustomerProfile c = new CustomerProfile();
        c.setId(UUID.randomUUID());
        c.setUser(user);
        return c;
    }

    private CaseParty makeParty(String name, String phone, String email) {
        CaseParty p = new CaseParty();
        p.setId(UUID.randomUUID());
        p.setFullName(name);
        p.setMobileNo(phone);
        p.setEmail(email);
        return p;
    }

    @BeforeEach
    void setUp() {
        partyMatchService = new PartyMatchService(customerProfileRepository, casePartyRepository, caseRepository);
    }

    @Test
    @DisplayName("Name + phone match returns HIGH confidence")
    void highConfidence() {
        User user = makeUser("Ram Sharma", "9800000001", "ram@email.com");
        when(customerProfileRepository.findMatches(eq(FIRM_ID), any(), any(), any()))
                .thenReturn(List.of(makeClient(user)));
        when(casePartyRepository.findMatches(any(), any(), any(), any()))
                .thenReturn(List.of());

        List<PartyMatchResult.Match> matches = partyMatchService.match(
                FIRM_ID, "Ram Sharma", "9800000001", null);

        assertEquals(1, matches.size());
        assertEquals("HIGH", matches.get(0).getConfidence());
    }

    @Test
    @DisplayName("Name + email match returns HIGH confidence")
    void highConfidenceEmail() {
        User user = makeUser("Ram Sharma", null, "ram@email.com");
        when(customerProfileRepository.findMatches(eq(FIRM_ID), any(), any(), any()))
                .thenReturn(List.of(makeClient(user)));
        when(casePartyRepository.findMatches(any(), any(), any(), any()))
                .thenReturn(List.of());

        List<PartyMatchResult.Match> matches = partyMatchService.match(
                FIRM_ID, "Ram Sharma", null, "ram@email.com");

        assertEquals("HIGH", matches.get(0).getConfidence());
    }

    @Test
    @DisplayName("Exact name match only returns MEDIUM confidence")
    void mediumConfidence() {
        User user = makeUser("Ram Sharma", null, null);
        when(customerProfileRepository.findMatches(eq(FIRM_ID), any(), any(), any()))
                .thenReturn(List.of(makeClient(user)));
        when(casePartyRepository.findMatches(any(), any(), any(), any()))
                .thenReturn(List.of());

        List<PartyMatchResult.Match> matches = partyMatchService.match(
                FIRM_ID, "Ram Sharma", null, null);

        assertEquals("MEDIUM", matches.get(0).getConfidence());
    }

    @Test
    @DisplayName("Partial name match only returns LOW confidence")
    void lowConfidence() {
        User user = makeUser("Ram Sharma", null, null);
        when(customerProfileRepository.findMatches(eq(FIRM_ID), any(), any(), any()))
                .thenReturn(List.of(makeClient(user)));
        when(casePartyRepository.findMatches(any(), any(), any(), any()))
                .thenReturn(List.of());

        List<PartyMatchResult.Match> matches = partyMatchService.match(
                FIRM_ID, "Ram", null, null);

        assertEquals("LOW", matches.get(0).getConfidence());
    }

    @Test
    @DisplayName("Matches both clients and case parties")
    void matchesBothSources() {
        User user = makeUser("Sita Devi", "9800000002", null);
        CaseParty party = makeParty("Sita Devi", "9800000002", null);
        UUID caseId = UUID.randomUUID();
        party.setCaseId(caseId);

        Case c = new Case();
        c.setId(caseId);
        c.setCaseNumber("APX-CIV-2026-00001");

        when(customerProfileRepository.findMatches(eq(FIRM_ID), any(), any(), any()))
                .thenReturn(List.of(makeClient(user)));
        when(casePartyRepository.findMatches(eq(FIRM_ID), any(), any(), any()))
                .thenReturn(List.of(party));
        when(caseRepository.findAllById(anyCollection()))
                .thenReturn(List.of(c));

        List<PartyMatchResult.Match> matches = partyMatchService.match(
                FIRM_ID, "Sita Devi", "9800000002", null);

        assertEquals(2, matches.size());
        assertTrue(matches.stream().allMatch(m -> "HIGH".equals(m.getConfidence())));

        // CASE_PARTY match must carry the case number
        PartyMatchResult.Match casePartyMatch = matches.stream()
                .filter(m -> "CASE_PARTY".equals(m.getSourceType()))
                .findFirst().orElseThrow();
        assertEquals("APX-CIV-2026-00001", casePartyMatch.getCaseNumber());
    }

    @Test
    @DisplayName("Returns empty list when no matches found")
    void noMatch() {
        when(customerProfileRepository.findMatches(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(casePartyRepository.findMatches(any(), any(), any(), any()))
                .thenReturn(List.of());

        List<PartyMatchResult.Match> matches = partyMatchService.match(
                FIRM_ID, "Unknown Person", null, null);

        assertTrue(matches.isEmpty());
    }
}
