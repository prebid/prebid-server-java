package org.prebid.server.hooks.modules.fiftyone.devicedetection.config;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection.DeviceRefiner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection.DeviceRefinerImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.pipeline.PipelineProvider;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionEntrypointHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@PropertySource(
        value = "classpath:/module-config/" + FiftyOneDeviceDetectionModule.CODE + ".yaml",
        factory = YamlPropertySourceFactory.class)
@ConditionalOnProperty(prefix = "hooks." + FiftyOneDeviceDetectionModule.CODE, name = "enabled", havingValue = "true")
public class FiftyOneDeviceDetectionModuleConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + FiftyOneDeviceDetectionModule.CODE)
    ModuleConfig moduleConfig() {
        return new ModuleConfig();
    }

    @Bean
    DeviceRefiner fiftyOneDeviceDetectionDeviceRefiner(ModuleConfig moduleConfig) throws Exception {
        return new DeviceRefinerImp(
                new PipelineProvider(
                        moduleConfig.getDataFile(),
                        moduleConfig.getPerformance()));
    }

    @Bean
    Module fiftyOneDeviceDetectionModule(ModuleConfig moduleConfig, DeviceRefiner deviceRefiner) {
        final Set<? extends Hook<?, ? extends InvocationContext>> hooks = Stream.of(
                new FiftyOneDeviceDetectionEntrypointHook(),
                new FiftyOneDeviceDetectionRawAuctionRequestHook(
                        moduleConfig.getAccountFilter(),
                        deviceRefiner)
        ).collect(Collectors.toSet());

        return new FiftyOneDeviceDetectionModule(hooks);
    }
}
