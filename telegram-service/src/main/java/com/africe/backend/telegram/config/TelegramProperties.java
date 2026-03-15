package com.africe.backend.telegram.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramProperties {

    private String token;
    private List<String> chatIds;
}
