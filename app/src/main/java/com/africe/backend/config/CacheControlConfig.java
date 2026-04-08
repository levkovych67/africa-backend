package com.africe.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

@Configuration
public class CacheControlConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // No-cache for all API endpoints — stock changes in real-time via SSE
        WebContentInterceptor noCache = new WebContentInterceptor();
        noCache.addCacheMapping(
                CacheControl.noStore(),
                "/api/v1/**"
        );
        registry.addInterceptor(noCache)
                .addPathPatterns("/api/v1/**");
    }
}
