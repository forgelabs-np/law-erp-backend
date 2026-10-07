package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.repository.CourtCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourtCaseRefGenerator {

    private final CourtCaseRepository courtCaseRepository;

    public String generate(String matterNumber, UUID matterId, CourtLevel courtLevel) {
        long n = courtCaseRepository.countByMatterIdAndCourtLevel(matterId, courtLevel) + 1;
        return matterNumber + "-" + levelCode(courtLevel) + n;
    }

    private String levelCode(CourtLevel level) {
        return switch (level) {
            case DISTRICT -> "DC";
            case HIGH -> "HC";
            case SUPREME -> "SC";
            case SPECIALIZED -> "SPC";
        };
    }
}
