package com.lawfirm.erp.common.storage;

import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Gives every existing firm a storage row and the default allocation on startup.
 *
 * <p>Firms predating the document store would otherwise have no row, and the first-upload
 * creation path would then be the only thing standing between them and an empty usage
 * screen. Runs once per boot and only inserts what is missing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageUsageBootstrap implements ApplicationRunner {

    private final FirmRepository firmRepository;
    private final FirmStorageUsageRepository repository;
    private final SystemConfigService systemConfigService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long created = 0;
        for (Firm firm : firmRepository.findAll()) {
            if (repository.findByFirmId(firm.getId()).isPresent()) {
                continue;
            }
            repository.save(FirmStorageUsage.builder()
                    .firmId(firm.getId())
                    .usedBytes(0L)
                    .quotaBytes(systemConfigService.storageDefaultQuotaBytes())
                    .updatedAt(LocalDateTime.now())
                    .build());
            created++;
        }
        if (created > 0) {
            log.info("Allocated the default storage quota to {} firm(s)", created);
        }
    }
}
