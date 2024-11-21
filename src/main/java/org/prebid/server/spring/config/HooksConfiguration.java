package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.HookCatalog;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class HooksConfiguration {

    @Bean
    HookCatalog hookCatalog(Collection<Module> modules, Set<String> moduleConfigPresenceSet) {
        return new HookCatalog(modules, moduleConfigPresenceSet);
    }

    @Bean
    HookStageExecutor hookStageExecutor(HooksConfigurationProperties hooksConfiguration,
                                        HookCatalog hookCatalog,
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
                timeoutFactory,
                vertx,
                clock,
                mapper,
                isConfigToInvokeRequired);
    }

    @Bean
    Set<String> moduleConfigPresenceSet(Collection<Module> modules, Environment environment) {
        if (modules.isEmpty() || !(environment instanceof ConfigurableEnvironment)) {
            return Collections.emptySet();
        }

        final Set<String> hooksPropertiesKeys = ((ConfigurableEnvironment) environment).getPropertySources().stream()
                .filter(EnumerablePropertySource.class::isInstance)
                .map(EnumerablePropertySource.class::cast)
                .map(EnumerablePropertySource::getPropertyNames)
                .flatMap(Arrays::stream)
                .filter(propertyName -> propertyName.startsWith("hooks."))
                .collect(Collectors.toSet());

        return modules.stream()
                .map(Module::code)
                .filter(code -> hooksPropertiesKeys.stream()
                        .anyMatch(key -> key.startsWith("hooks.%s".formatted(code))))
                .collect(Collectors.toSet());
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
