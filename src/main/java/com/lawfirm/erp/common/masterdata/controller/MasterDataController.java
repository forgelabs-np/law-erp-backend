package com.lawfirm.erp.common.masterdata.controller;

import com.lawfirm.erp.common.constant.MasterDataConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.common.masterdata.dto.DistrictResponse;
import com.lawfirm.erp.common.masterdata.dto.ProvinceResponse;
import com.lawfirm.erp.common.masterdata.service.MasterDataService;
import com.lawfirm.erp.common.masterdata.service.MasterDataSeeder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reference data for the whole system — Nepal provinces and districts.
 * Reads are served from the {@code masterData} Ehcache (24h TTL); the refresh
 * endpoint re-seeds from classpath JSON, evicts the cache and warms it again.
 */
@RestController
@RequestMapping("/api/v1/master-data")
@RequiredArgsConstructor
@Tag(name = "Master Data", description = "Reference data (Nepal provinces & districts) — ehcache-backed reads")
public class MasterDataController {

    private final MasterDataService masterDataService;
    private final MasterDataSeeder masterDataSeeder;
    private final ResponseHandler responseHandler;

    @GetMapping("/provinces")
    @Operation(summary = MasterDataConstants.GET_PROVINCES_SUMMARY, description = MasterDataConstants.GET_PROVINCES_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> getProvinces() {
        return responseHandler.ok(masterDataService.getAllProvinces(), "Provinces fetched successfully");
    }

    @GetMapping("/provinces/{provinceId}/districts")
    @Operation(summary = MasterDataConstants.GET_DISTRICTS_BY_PROVINCE_SUMMARY, description = MasterDataConstants.GET_DISTRICTS_BY_PROVINCE_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> getDistrictsByProvince(
            @PathVariable UUID provinceId) {
        return responseHandler.ok(
                masterDataService.getDistrictsByProvince(provinceId),
                "Districts fetched successfully");
    }

    @GetMapping("/districts")
    @Operation(summary = MasterDataConstants.GET_ALL_DISTRICTS_SUMMARY, description = MasterDataConstants.GET_ALL_DISTRICTS_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> getDistricts() {
        return responseHandler.ok(masterDataService.getAllDistricts(), "Districts fetched successfully");
    }

    @GetMapping("/cache/stats")
    @Operation(summary = MasterDataConstants.GET_CACHE_STATS_SUMMARY, description = MasterDataConstants.GET_CACHE_STATS_DESCRIPTION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheStats() {
        return responseHandler.ok(masterDataService.cacheStats(), "Cache stats fetched successfully");
    }

    @PostMapping("/cache/refresh")
    @Operation(summary = MasterDataConstants.REFRESH_CACHE_SUMMARY, description = MasterDataConstants.REFRESH_CACHE_DESCRIPTION)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FIRM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshCache() {
        masterDataSeeder.seed();
        masterDataService.evictCache();

        // Warm the cache so the first user request doesn't pay a cold miss.
        int provinceCount = masterDataService.getAllProvinces().size();
        masterDataService.getAllDistricts();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provincesCached", provinceCount);
        result.put("cache", masterDataService.cacheStats());
        return responseHandler.ok(result, "Master data cache refreshed successfully");
    }
}
