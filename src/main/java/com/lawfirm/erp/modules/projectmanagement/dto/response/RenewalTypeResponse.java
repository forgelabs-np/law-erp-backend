package com.lawfirm.erp.modules.projectmanagement.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RenewalTypeResponse {

    private Long id;
    private String name;
    private String description;
    private boolean system;
    private boolean active;
}
