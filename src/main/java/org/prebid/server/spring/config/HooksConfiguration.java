package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.execution.HookCatalog;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.util.Collection;

@Configuration
public class HooksConfiguration {

    @Bean
    HookCatalog hookCatalog(Collection<Module> modules) {
        return new HookCatalog(modules);
    }

    @Bean
    HookStageExecutor hookStageExecutor(HooksConfigurationProperties hooksConfiguration,
                                        HookCatalog hookCatalog,
                                        TimeoutFactory timeoutFactory,
                                        Vertx vertx,
                                        Clock clock,
                                        JacksonMapper mapper) {

        return HookStageExecutor.create(
                hooksConfiguration.getHostExecutionPlan(),
                hooksConfiguration.getDefaultAccountExecutionPlan(),
                hookCatalog,
                timeoutFactory,
                vertx,
                clock,
                mapper);
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

        String hostExecutionPlan;

        String defaultAccountExecutionPlan;
    }
}
