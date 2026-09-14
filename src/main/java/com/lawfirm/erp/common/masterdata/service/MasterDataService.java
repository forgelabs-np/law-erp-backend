package com.lawfirm.erp.common.masterdata.service;

import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.masterdata.dto.DistrictResponse;
import com.lawfirm.erp.common.masterdata.dto.ProvinceResponse;
import com.lawfirm.erp.common.masterdata.entity.District;
import com.lawfirm.erp.common.masterdata.entity.Province;
import com.lawfirm.erp.common.masterdata.repository.DistrictRepository;
import com.lawfirm.erp.common.masterdata.repository.ProvinceRepository;
import com.lawfirm.erp.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-mostly reference data. Every read is cache-aside through the {@code masterData}
 * Ehcache (24h TTL); {@link #evictCache()} drops everything when the data is re-seeded.
 */
@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final CacheManager cacheManager;

    @Cacheable(cacheNames = CacheConfig.MASTER_DATA_CACHE, key = "'provinces'",
            unless = "#result == null || #result.isEmpty()")
    public List<ProvinceResponse> getAllProvinces() {
        List<Province> provinces = provinceRepository.findAllByOrderByDisplayOrderAscIdAsc();
        Map<UUID, Long> counts = districtRepository.findAll().stream()
                .collect(Collectors.groupingBy(District::getProvinceId, Collectors.counting()));

        return provinces.stream()
                .map(p -> toProvinceResponse(p, counts.getOrDefault(p.getId(), 0L).intValue()))
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = CacheConfig.MASTER_DATA_CACHE, key = "'districts'",
            unless = "#result == null || #result.isEmpty()")
    public List<DistrictResponse> getAllDistricts() {
        Map<UUID, Province> provinces = provinceRepository.findAll().stream()
                .collect(Collectors.toMap(Province::getId, Function.identity()));

        return districtRepository.findAllByOrderByNameEnAsc().stream()
                .map(d -> toDistrictResponse(d, provinces.get(d.getProvinceId())))
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = CacheConfig.MASTER_DATA_CACHE, key = "'districts:' + #provinceId",
            unless = "#result == null || #result.isEmpty()")
    public List<DistrictResponse> getDistrictsByProvince(UUID provinceId) {
        Province province = provinceRepository.findById(provinceId)
                .orElseThrow(() -> new ResourceNotFoundException("Province not found: " + provinceId));
        return districtRepository.findByProvinceIdOrderByNameEnAsc(provinceId).stream()
                .map(d -> toDistrictResponse(d, province))
                .collect(Collectors.toList());
    }

    /** Drops every entry — call after re-seeding so clients never see stale reference data. */
    @CacheEvict(cacheNames = CacheConfig.MASTER_DATA_CACHE, allEntries = true)
    public void evictCache() {
        // no-op body; the annotation does the eviction
    }

    /**
     * Cache telemetry for ops: current size vs configured capacity and TTL tell you
     * whether the cache is being used and how fresh it is. (Fine-grained hit/miss
     * counters live in the JSR-107 provider; hook Micrometer's cache metrics if needed.)
     */
    public Map<String, Object> cacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cacheName", CacheConfig.MASTER_DATA_CACHE);
        stats.put("ttlHours", CacheConfig.MASTER_DATA_TTL_HOURS);
        stats.put("maxHeapEntries", CacheConfig.MASTER_DATA_HEAP_ENTRIES);

        org.springframework.cache.Cache springCache = cacheManager.getCache(CacheConfig.MASTER_DATA_CACHE);
        if (springCache == null || !(springCache.getNativeCache() instanceof javax.cache.Cache)) {
            stats.put("available", false);
            return stats;
        }

        javax.cache.Cache<?, ?> nativeCache = (javax.cache.Cache<?, ?>) springCache.getNativeCache();
        stats.put("available", true);
        stats.put("size", countEntries(nativeCache));
        return stats;
    }

    private long countEntries(javax.cache.Cache<?, ?> cache) {
        long n = 0;
        for (javax.cache.Cache.Entry<?, ?> ignored : cache) {
            n++;
        }
        return n;
    }

    private ProvinceResponse toProvinceResponse(Province p, int districtCount) {
        return ProvinceResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .nameEn(p.getNameEn())
                .nameNp(p.getNameNp())
                .capitalEn(p.getCapitalEn())
                .capitalNp(p.getCapitalNp())
                .areaKm2(p.getAreaKm2())
                .population2021(p.getPopulation2021())
                .districtCount(districtCount)
                .build();
    }

    private DistrictResponse toDistrictResponse(District d, Province province) {
        return DistrictResponse.builder()
                .id(d.getId())
                .code(d.getCode())
                .nameEn(d.getNameEn())
                .nameNp(d.getNameNp())
                .headquarters(d.getHeadquarters())
                .provinceId(d.getProvinceId())
                .provinceCode(d.getProvinceCode())
                .provinceNameEn(province != null ? province.getNameEn() : null)
                .areaKm2(d.getAreaKm2())
                .population2021(d.getPopulation2021())
                .build();
    }
}
