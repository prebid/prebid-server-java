package org.prebid.server.it.hooks;

import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestHooksConfiguration {

    @Bean
    Module sampleItModule(JacksonMapper mapper) {
        return new SampleItModule(mapper);
    }
}
