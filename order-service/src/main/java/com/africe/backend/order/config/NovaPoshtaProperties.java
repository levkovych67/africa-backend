package com.africe.backend.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nova-poshta")
public class NovaPoshtaProperties {

    private String apiKey;
    private String baseUrl;
}
