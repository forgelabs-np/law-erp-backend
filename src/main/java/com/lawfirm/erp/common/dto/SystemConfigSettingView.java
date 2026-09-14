package com.lawfirm.erp.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-facing view of one system_config row. Carries the UI/validation metadata
 * (group, input type, allowed values, description) plus the current value.
 *
 * Values are returned to admins as plaintext (decrypted) — admins own these
 * settings and may need to inspect them. Encryption protects the value at rest.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigSettingView {

    private String configKey;
    private String configGroup;
    /** Current value, decrypted for admins. */
    private String value;
    private String inputType;
    private String allowedValues;
    private String description;
    private boolean active;
    private boolean allowEdit;
    /** True when the value is stored AES-256 encrypted. */
    private boolean sensitive;
}
