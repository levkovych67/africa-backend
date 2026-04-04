package com.africe.backend.order.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "monobank")
public class MonobankProperties {

    private String token;
    private String webhookBaseUrl;
}
