package com.lawfirm.erp.common.constant;

public final class MasterDataConstants {

    private MasterDataConstants() {}

    // MasterDataController
    public static final String GET_PROVINCES_SUMMARY = "All provinces";
    public static final String GET_PROVINCES_DESCRIPTION = "All 7 Nepal provinces (cached). Each includes districtCount.";

    public static final String GET_DISTRICTS_BY_PROVINCE_SUMMARY = "Districts of a province";
    public static final String GET_DISTRICTS_BY_PROVINCE_DESCRIPTION = "Districts belonging to one province (cached per province)";

    public static final String GET_ALL_DISTRICTS_SUMMARY = "All districts";
    public static final String GET_ALL_DISTRICTS_DESCRIPTION = "All 77 Nepal districts with province info (cached)";

    public static final String GET_CACHE_STATS_SUMMARY = "Cache telemetry";
    public static final String GET_CACHE_STATS_DESCRIPTION = "Hits/misses/evictions/size of the masterData Ehcache — for ops sanity checks";

    public static final String REFRESH_CACHE_SUMMARY = "Refresh master data cache";
    public static final String REFRESH_CACHE_DESCRIPTION = "Re-seeds from classpath JSON, evicts the cache and warms it. Idempotent.";
}
