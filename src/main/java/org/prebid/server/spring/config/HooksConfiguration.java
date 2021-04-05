package org.prebid.server.spring.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.json.JacksonMapper;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
public class HooksConfiguration {

    @Bean
    HookStageExecutor hookStageExecutor(HooksConfigurationProperties hooksConfiguration,
                                        JacksonMapper mapper) {

        return HookStageExecutor.create(hooksConfiguration.getExecutionPlan(), mapper);
    }

    @Bean
    @ConfigurationProperties("hooks")
    HooksConfigurationProperties hooksConfigurationProperties() {
        return new HooksConfigurationProperties();
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class HooksConfigurationProperties {

        String executionPlan;
    }
}
