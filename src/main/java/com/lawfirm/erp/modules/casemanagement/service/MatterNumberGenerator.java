package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;

/**
 * Matter number: {firmCode}-MAT-{year}-{seq:05d} — generated once, never changes.
 * e.g. APX-MAT-2026-00001
 */
@Service
@RequiredArgsConstructor
public class MatterNumberGenerator {

    private static final int PAGE_SIZE = 1;

    private final MatterRepository matterRepository;

    public String generate(String firmCode) {
        int year = Year.now().getValue();
        String pattern = firmCode + "-MAT-" + year + "-%";

        List<String> results = matterRepository.findMaxMatterNumberByPattern(
                FirmContextHolder.getFirmId(), pattern, PageRequest.of(0, PAGE_SIZE));

        if (!results.isEmpty()) {
            String max = results.get(0);
            int seq = Integer.parseInt(max.substring(max.lastIndexOf('-') + 1));
            return String.format("%s-MAT-%d-%05d", firmCode, year, seq + 1);
        }
        return String.format("%s-MAT-%d-%05d", firmCode, year, 1);
    }
}
