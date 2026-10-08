package com.lawfirm.erp.dto.firm.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LogoResponse {

    private String logoUrl;
    private boolean logoAllowed;
}
