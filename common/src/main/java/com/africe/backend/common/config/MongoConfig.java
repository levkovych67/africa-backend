package com.africe.backend.common.config;

import com.mongodb.WriteConcern;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoWriteConcernCustomizer() {
        return builder -> builder.writeConcern(WriteConcern.MAJORITY);
    }
}
