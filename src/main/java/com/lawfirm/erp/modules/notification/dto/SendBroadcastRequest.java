package com.lawfirm.erp.modules.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendBroadcastRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "Body is required")
    @Size(max = 2000, message = "Body must be at most 2000 characters")
    private String body;

    /** "ALL" or a role code (e.g. ADVOCATE, PARALEGAL, CLIENT). */
    @NotBlank(message = "Audience is required")
    private String audience;
}
