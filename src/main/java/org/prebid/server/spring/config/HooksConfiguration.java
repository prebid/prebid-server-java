package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.HookCatalog;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.provider.HookProviderFactory;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Value;
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
    HookProviderFactory hookProviderFactory(HookCatalog hookCatalog) {
        return new HookProviderFactory(hookCatalog, ObjectMapperProvider.mapper());
    }

    @Bean
    HookStageExecutor hookStageExecutor(HooksConfigurationProperties hooksConfiguration,
                                        HookCatalog hookCatalog,
                                        HookProviderFactory hookProviderFactory,
                                        TimeoutFactory timeoutFactory,
                                        Vertx vertx,
                                        Clock clock,
                                        JacksonMapper mapper,
                                        @Value("${settings.modules.require-config-to-invoke:false}")
                                        boolean isConfigToInvokeRequired) {

        return HookStageExecutor.create(
                hooksConfiguration.getHostExecutionPlan(),
                hooksConfiguration.getDefaultAccountExecutionPlan(),
                hookCatalog,
                hookProviderFactory,
                timeoutFactory,
                vertx,
                clock,
                mapper,
                isConfigToInvokeRequired);
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
