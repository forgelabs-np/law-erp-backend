package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.storage.StorageService;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.StorageOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirmStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    private static final long MAX_BYTES = 200L * 1024;
    private static final String KEY_PREFIX = "firms/";

    private final StorageService storageService;

    public LogoUploadResult storeFirmLogo(UUID firmId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = lowerTrimmedExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException(
                    "Logo must be an image (png, jpg, jpeg or webp). Got: " + extensionOr(originalFilename, "unknown type"));
        }
        long size = file.getSize();
        if (size > MAX_BYTES) {
            throw new BusinessRuleException(
                    "Logo must be at most 200 KiB (" + MAX_BYTES + " bytes). Got " + size + " bytes.");
        }
        if (size <= 0) {
            throw new BusinessRuleException("Logo file is empty");
        }

        String storedKey = objectKey(firmId, extension);
        try {
            storageService.put(storedKey, file.getInputStream(), size, file.getContentType());
        } catch (Exception e) {
            throw new StorageOperationException("Could not store the firm logo", e);
        }
        log.debug("Stored firm logo key={} contentType={} bytes={}", storedKey, file.getContentType(), size);

        return new LogoUploadResult(storedKey, storageService.presignFirmLogo(storedKey));
    }

    /**
     * Removes the previously stored logo object. The prior URL is whatever {@code firm.logoUrl}
     * held (a presigned link, or a stored key), so the object key is recovered from its path.
     */
    public void deleteFirmLogo(UUID firmId, String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            return;
        }
        String key = logoKeyFromUrl(logoUrl, firmId);
        try {
            storageService.delete(key);
        } catch (StorageOperationException e) {
            log.warn("Could not delete the prior firm logo key={}: {}", key, e.getMessage());
        }
    }

    private String logoKeyFromUrl(String url, UUID firmId) {
        String path = url.split("\\?", 2)[0];
        String name = path.substring(path.lastIndexOf('/') + 1);
        return KEY_PREFIX + firmId + "/" + name;
    }

    private String objectKey(UUID firmId, String extension) {
        return KEY_PREFIX + firmId + "/logo." + extension;
    }

    private String lowerTrimmedExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).trim().toLowerCase();
    }

    private String extensionOr(String filename, String fallback) {
        String ext = lowerTrimmedExtension(filename);
        return ext == null ? fallback : ext;
    }

    public record LogoUploadResult(String storedKey, String presignUrl) {}
}
