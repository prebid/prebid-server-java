package org.prebid.server.spring.config;

import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {

    @Bean
    JacksonMapper jacksonMapper() {
        return new JacksonMapper(ObjectMapperProvider.mapper());
    }
}
