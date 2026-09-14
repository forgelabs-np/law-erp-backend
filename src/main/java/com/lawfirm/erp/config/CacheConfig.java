package com.lawfirm.erp.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.spi.CachingProvider;

/**
 * Caching setup — Ehcache 3 (JSR-107 provider), the cache backend supported by
 * Spring Framework 7 / Spring Boot 4.
 *
 * Principles applied:
 *  - Cache read-heavy, write-rarely reference data only (master data) — never tenant/user data.
 *  - Single named cache {@code masterData}; distinct keys per query, so eviction is granular.
 *  - TTL expiry (24h, from creation) means stale entries self-heal even if no explicit eviction runs.
 *  - Explicit {@code @CacheEvict(allEntries = true)} on the refresh path for immediate consistency.
 *  - Cache-aside (read-through): the service methods are the only place the cache is touched.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MASTER_DATA_CACHE = "masterData";

    public static final int MASTER_DATA_TTL_HOURS = 24;

    /** Heap capacity in entries (Ehcache-specific; JSR-107 config has no capacity knob). */
    public static final int MASTER_DATA_HEAP_ENTRIES = 500;

    @Bean
    public JCacheCacheManager cacheManager() {
        CachingProvider provider = Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider");
        javax.cache.CacheManager jcacheManager = provider.getCacheManager();

        if (jcacheManager.getCache(MASTER_DATA_CACHE) == null) {
            MutableConfiguration<String, Object> config = new MutableConfiguration<>();
            config.setTypes(String.class, Object.class);
            config.setStoreByValue(true);   // entries are deep-copied — safe against external mutation
            config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
                    new javax.cache.expiry.Duration(java.util.concurrent.TimeUnit.HOURS, MASTER_DATA_TTL_HOURS)));
            jcacheManager.createCache(MASTER_DATA_CACHE, config);
        }

        return new JCacheCacheManager(jcacheManager);
    }
}
