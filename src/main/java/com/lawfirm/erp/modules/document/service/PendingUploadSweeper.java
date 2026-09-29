package com.lawfirm.erp.modules.document.service;

import com.lawfirm.erp.common.storage.StorageService;
import com.lawfirm.erp.modules.document.entity.Document;
import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Clears uploads that were started and never confirmed.
 *
 * <p>A browser that closes mid-upload leaves a {@code PENDING_UPLOAD} row forever, and if the
 * bytes did land they would sit in the bucket unaccounted for. Once the upload window has
 * passed, the row is discarded — it never became a document, so there is nothing to retain —
 * and any partial object is removed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingUploadSweeper {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    @Scheduled(fixedDelayString = "${storage.pending-upload-sweep-ms:900000}",
            initialDelayString = "${storage.pending-upload-sweep-initial-ms:300000}")
    @Transactional
    public void sweep() {
        List<Document> abandoned = documentRepository
                .findByStatusAndUploadExpiresAtBefore(DocumentStatus.PENDING_UPLOAD, LocalDateTime.now());
        if (abandoned.isEmpty()) {
            return;
        }
        for (Document document : abandoned) {
            try {
                storageService.delete(document.getStorageKey());
            } catch (Exception e) {
                log.debug("No object to clean up for abandoned upload {}: {}", document.getId(), e.getMessage());
            }
        }
        documentRepository.deleteAll(abandoned);
        log.info("Discarded {} abandoned upload(s)", abandoned.size());
    }
}
