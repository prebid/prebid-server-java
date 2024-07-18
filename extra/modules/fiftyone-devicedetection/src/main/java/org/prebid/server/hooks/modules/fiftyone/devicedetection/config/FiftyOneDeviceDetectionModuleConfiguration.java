package org.prebid.server.hooks.modules.fiftyone.devicedetection.config;

import fiftyone.devicedetection.DeviceDetectionPipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionEntrypointHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConditionalOnProperty(prefix = "hooks." + FiftyOneDeviceDetectionModule.CODE, name = "enabled", havingValue = "true")
public class FiftyOneDeviceDetectionModuleConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + FiftyOneDeviceDetectionModule.CODE)
    ModuleConfig moduleConfig() {
        return new ModuleConfig();
    }

    @Bean
    Pipeline pipeline(ModuleConfig moduleConfig) throws Exception {
        return new PipelineBuilder(moduleConfig).build(new DeviceDetectionPipelineBuilder());
    }

    @Bean
    DeviceEnricher deviceEnricher(Pipeline pipeline) {
        return new DeviceEnricher(pipeline);
    }

    @Bean
    Module fiftyOneDeviceDetectionModule(ModuleConfig moduleConfig, DeviceEnricher deviceEnricher) {
        final Set<? extends Hook<?, ? extends InvocationContext>> hooks = Set.of(
                new FiftyOneDeviceDetectionEntrypointHook(),
                new FiftyOneDeviceDetectionRawAuctionRequestHook(moduleConfig.getAccountFilter(), deviceEnricher)
        );

        return new FiftyOneDeviceDetectionModule(hooks);
    }
}
