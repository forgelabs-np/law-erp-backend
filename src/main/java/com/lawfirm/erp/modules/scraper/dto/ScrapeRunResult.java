package com.lawfirm.erp.modules.scraper.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScrapeRunResult {

    private Integer courtId;
    private boolean success;
    private int rows;
    private String error;
}
