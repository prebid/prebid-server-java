package org.prebid.server.spring.config;

import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfiguration {

    @Bean
    JacksonMapper jacksonMapper() {
        return new JacksonMapper(ObjectMapperProvider.mapper());
    }

    @Bean
    JsonMerger jsonMerger(JacksonMapper mapper) {
        return new JsonMerger(mapper);
    }
}
