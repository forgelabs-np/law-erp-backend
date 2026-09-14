package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.customer.entity.CustomerProfile;
import com.lawfirm.erp.customer.repository.CustomerProfileRepository;
import com.lawfirm.erp.modules.casemanagement.dto.response.PartyMatchResult;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dedup on party entry: matches the entered name/phone/email against existing
 * client profiles and matter parties within the same firm, ranked HIGH/MEDIUM/LOW.
 */
@Service
@RequiredArgsConstructor
public class PartyMatchService {

    private final CustomerProfileRepository customerProfileRepository;
    private final MatterPartyRepository matterPartyRepository;
    private final MatterRepository matterRepository;

    public List<PartyMatchResult.Match> match(UUID firmId, String name, String mobileNo, String email) {
        List<PartyMatchResult.Match> results = new ArrayList<>();

        for (CustomerProfile c : customerProfileRepository.findMatches(firmId, name, mobileNo, email)) {
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

        List<MatterParty> parties = matterPartyRepository.findMatches(firmId, name, mobileNo, email);
        Map<UUID, String> matterNumbers = parties.isEmpty() ? Map.of()
                : matterRepository.findAllById(
                        parties.stream().map(MatterParty::getMatterId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Matter::getId, Matter::getMatterNumber));

        for (MatterParty p : parties) {
            results.add(PartyMatchResult.Match.builder()
                    .sourceType("MATTER_PARTY")
                    .sourceId(p.getId())
                    .caseNumber(matterNumbers.get(p.getMatterId()))
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
        if (phoneMatch || emailMatch) return "MEDIUM";
        if (namePartial) return "LOW";
        return "LOW";
    }
}
