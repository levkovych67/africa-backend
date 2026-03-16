package com.africe.backend.order.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                new CaffeineCache("novaPoshtaCities",
                        Caffeine.newBuilder()
                                .expireAfterWrite(1, TimeUnit.HOURS)
                                .maximumSize(200)
                                .build()),
                new CaffeineCache("novaPoshtaWarehouses",
                        Caffeine.newBuilder()
                                .expireAfterWrite(24, TimeUnit.HOURS)
                                .maximumSize(500)
                                .build())
        ));
        return cacheManager;
    }
}
