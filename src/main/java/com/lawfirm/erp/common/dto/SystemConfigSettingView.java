package com.lawfirm.erp.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigSettingView {

    private String configKey;
    private String configGroup;
    private String value;
    private String inputType;
    private String allowedValues;
    private String description;
    private boolean active;
    private boolean allowEdit;
    private boolean sensitive;
}
