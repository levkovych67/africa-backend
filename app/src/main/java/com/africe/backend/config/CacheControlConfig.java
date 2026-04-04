package com.africe.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheControlConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Public cacheable endpoints — 1 hour
        WebContentInterceptor publicCache = new WebContentInterceptor();
        publicCache.addCacheMapping(
                CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic(),
                "/api/v1/products/**",
                "/api/v1/artists/**"
        );
        registry.addInterceptor(publicCache)
                .addPathPatterns("/api/v1/products/**", "/api/v1/artists/**");

        // No-store for admin, orders, mutations
        WebContentInterceptor noStoreCache = new WebContentInterceptor();
        noStoreCache.addCacheMapping(
                CacheControl.noStore(),
                "/api/v1/admin/**",
                "/api/v1/orders/**",
                "/api/v1/auth/**",
                "/api/v1/payments/**"
        );
        registry.addInterceptor(noStoreCache)
                .addPathPatterns("/api/v1/admin/**", "/api/v1/orders/**",
                        "/api/v1/auth/**", "/api/v1/payments/**");
    }
}
