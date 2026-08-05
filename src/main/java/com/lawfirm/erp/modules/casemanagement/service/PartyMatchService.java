package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.customer.entity.CustomerProfile;
import com.lawfirm.erp.customer.repository.CustomerProfileRepository;
import com.lawfirm.erp.modules.casemanagement.dto.response.PartyMatchResult;
import com.lawfirm.erp.modules.casemanagement.entity.Case;
import com.lawfirm.erp.modules.casemanagement.entity.CaseParty;
import com.lawfirm.erp.modules.casemanagement.repository.CasePartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartyMatchService {

    private final CustomerProfileRepository customerProfileRepository;
    private final CasePartyRepository casePartyRepository;
    private final CaseRepository caseRepository;

    public List<PartyMatchResult.Match> match(UUID firmId, String name, String mobileNo, String email) {
        List<PartyMatchResult.Match> results = new ArrayList<>();

        // Match against client profiles
        List<CustomerProfile> clients = customerProfileRepository.findMatches(firmId, name, mobileNo, email);
        for (CustomerProfile c : clients) {
            String fn = c.getUser() != null ? c.getUser().getFullName() : null;
            String mn = c.getUser() != null ? c.getUser().getMobileNo() : null;
            String em = c.getUser() != null ? c.getUser().getEmail() : null;
            results.add(PartyMatchResult.Match.builder()
                    .sourceType("CLIENT")
                    .sourceId(c.getId())
                    .fullName(fn)
                    .mobileNo(mn)
                    .email(em)
                    .confidence(computeConfidence(name, fn, mobileNo, mn, email, em))
                    .build());
        }

        // Match against existing case parties
        List<CaseParty> parties = casePartyRepository.findMatches(firmId, name, mobileNo, email);

        // Batch-resolve case numbers for the matched parties (single query, no N+1)
        Map<UUID, String> caseNumbers = parties.isEmpty() ? Map.of()
                : caseRepository.findAllById(parties.stream()
                        .map(CaseParty::getCaseId)
                        .distinct()
                        .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(Case::getId, Case::getCaseNumber));

        for (CaseParty p : parties) {
            results.add(PartyMatchResult.Match.builder()
                    .sourceType("CASE_PARTY")
                    .sourceId(p.getId())
                    .caseNumber(caseNumbers.get(p.getCaseId()))
                    .fullName(p.getFullName())
                    .mobileNo(p.getMobileNo())
                    .email(p.getEmail())
                    .confidence(computeConfidence(name, p.getFullName(), mobileNo, p.getMobileNo(), email, p.getEmail()))
                    .build());
        }

        return results;
    }

    private String computeConfidence(String inputName, String dbName,
                                      String inputPhone, String dbPhone,
                                      String inputEmail, String dbEmail) {
        boolean nameMatch = inputName != null && dbName != null
                && inputName.equalsIgnoreCase(dbName);
        boolean phoneMatch = inputPhone != null && dbPhone != null
                && inputPhone.equals(dbPhone);
        boolean emailMatch = inputEmail != null && dbEmail != null
                && inputEmail.equalsIgnoreCase(dbEmail);
        boolean namePartial = inputName != null && dbName != null
                && (inputName.toLowerCase().contains(dbName.toLowerCase())
                || dbName.toLowerCase().contains(inputName.toLowerCase()));

        if (nameMatch && (phoneMatch || emailMatch)) return "HIGH";
        if (nameMatch) return "MEDIUM";
        if (namePartial && (phoneMatch || emailMatch)) return "MEDIUM";
        if (namePartial) return "LOW";
        if (phoneMatch || emailMatch) return "MEDIUM";
        return "LOW";
    }
}
