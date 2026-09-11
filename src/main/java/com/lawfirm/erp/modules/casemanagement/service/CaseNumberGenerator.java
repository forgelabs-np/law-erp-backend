package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.modules.casemanagement.enums.CaseType;
import com.lawfirm.erp.modules.casemanagement.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class CaseNumberGenerator {

    private static final int PAGE_SIZE = 1;
    private final CaseRepository caseRepository;

    public String generate(String firmCode, CaseType caseType) {
        String typePrefix = caseType == CaseType.CIVIL ? "CIV" : "CRM";
        int year = Year.now().getValue();
        String pattern = firmCode + "-" + typePrefix + "-" + year + "-%";

        List<String> results = caseRepository.findMaxCaseNumberByPattern(FirmContextHolder.getFirmId(), pattern, PageRequest.of(0, PAGE_SIZE));
        if (!results.isEmpty()) {
            String max = results.get(0);
            int seq = Integer.parseInt(max.substring(max.lastIndexOf('-') + 1));
            return String.format("%s-%s-%d-%05d", firmCode, typePrefix, year, seq + 1);
        }
        return String.format("%s-%s-%d-%05d", firmCode, typePrefix, year, 1);
    }
}
