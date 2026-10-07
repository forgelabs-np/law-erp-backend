package com.lawfirm.erp.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparently encrypts a String column at rest with the same AES-256-GCM key used for system
 * config secrets ({@code config.encryption.key}). Applied per-field with {@code @Convert} — never
 * auto-applied, so only explicitly-marked columns (MFA secrets) change behaviour.
 *
 * <p>Hibernate instantiates this converter directly, so it reaches the encryption key through the
 * static {@link ConfigEncryptionUtil#getInstance()} reference rather than dependency injection.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return ConfigEncryptionUtil.getInstance().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return ConfigEncryptionUtil.getInstance().decrypt(dbData);
        } catch (Exception e) {
            // A value written before this column was encrypted is plaintext and will not decrypt.
            // Return it as-is so existing rows keep working; it is re-encrypted on next save.
            return dbData;
        }
    }
}
