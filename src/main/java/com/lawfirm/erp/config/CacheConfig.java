package com.lawfirm.erp.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.spi.CachingProvider;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MASTER_DATA_CACHE = "masterData";

    public static final int MASTER_DATA_TTL_HOURS = 24;

    public static final int MASTER_DATA_HEAP_ENTRIES = 500;

    @Bean
    public JCacheCacheManager cacheManager() {
        CachingProvider provider = Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider");
        javax.cache.CacheManager jcacheManager = provider.getCacheManager();

        if (jcacheManager.getCache(MASTER_DATA_CACHE) == null) {
            MutableConfiguration<String, Object> config = new MutableConfiguration<>();
            config.setTypes(String.class, Object.class);
            config.setStoreByValue(true);
            config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
                    new javax.cache.expiry.Duration(java.util.concurrent.TimeUnit.HOURS, MASTER_DATA_TTL_HOURS)));
            jcacheManager.createCache(MASTER_DATA_CACHE, config);
        }

        return new JCacheCacheManager(jcacheManager);
    }
}
