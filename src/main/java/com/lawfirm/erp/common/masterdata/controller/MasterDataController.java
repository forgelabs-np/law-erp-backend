package com.lawfirm.erp.common.masterdata.controller;

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
    @Operation(summary = "All provinces", description = "All 7 Nepal provinces (cached). Each includes districtCount.")
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> getProvinces() {
        return responseHandler.ok(masterDataService.getAllProvinces(), "Provinces fetched successfully");
    }

    @GetMapping("/provinces/{provinceId}/districts")
    @Operation(summary = "Districts of a province", description = "Districts belonging to one province (cached per province)")
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> getDistrictsByProvince(
            @PathVariable UUID provinceId) {
        return responseHandler.ok(
                masterDataService.getDistrictsByProvince(provinceId),
                "Districts fetched successfully");
    }

    @GetMapping("/districts")
    @Operation(summary = "All districts", description = "All 77 Nepal districts with province info (cached)")
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> getDistricts() {
        return responseHandler.ok(masterDataService.getAllDistricts(), "Districts fetched successfully");
    }

    @GetMapping("/cache/stats")
    @Operation(summary = "Cache telemetry", description = "Hits/misses/evictions/size of the masterData Ehcache — for ops sanity checks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheStats() {
        return responseHandler.ok(masterDataService.cacheStats(), "Cache stats fetched successfully");
    }

    @PostMapping("/cache/refresh")
    @Operation(summary = "Refresh master data cache", description = "Re-seeds from classpath JSON, evicts the cache and warms it. Idempotent.")
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
