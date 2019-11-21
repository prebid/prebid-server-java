package org.prebid.server.spring.config;

import io.vertx.core.json.Json;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {

    static {
        ObjectMapperConfigurer.configure();
    }

    @Bean
    JacksonMapper jacksonMapper() {
        return new JacksonMapper(Json.mapper);
    }
}
