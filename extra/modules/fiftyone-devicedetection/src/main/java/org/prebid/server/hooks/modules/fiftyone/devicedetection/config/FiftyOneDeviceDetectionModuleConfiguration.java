package org.prebid.server.hooks.modules.fiftyone.devicedetection.config;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.DeviceInfoClone;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceDetector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.DeviceDetectorImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.adapters.DeviceMirror;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.DevicePatchPlannerImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.DeviceInfoPatcherImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.DeviceTypeConverterImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.PriorityEvidenceSelectorImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.pipelinebuilders.PipelineBuilderSpawnerImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.pipelinebuilders.PipelinePerformanceConfigurator;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.pipelinebuilders.PipelineSupplierImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.pipelinebuilders.PipelineUpdateConfigurator;
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
    DeviceDetector fiftyOneDeviceDetectionDeviceDetector(ModuleConfig moduleConfig) throws Exception {
        final var pipelineBuilder = new PipelineBuilderSpawnerImp().makeBuilder(moduleConfig.getDataFile());
        new PipelineUpdateConfigurator().applyProperties(pipelineBuilder, moduleConfig.getDataFile().getUpdate());
        new PipelinePerformanceConfigurator().applyProperties(pipelineBuilder, moduleConfig.getPerformance());
        return new DeviceDetectorImp(
                new PipelineSupplierImp(pipelineBuilder),
                new PriorityEvidenceSelectorImp(),
                new DeviceTypeConverterImp(),
                new DeviceInfoPatcherImp<>(DeviceInfoClone.BUILDER_METHOD_SET::makeAdapter));
    }

    @Bean
    Module fiftyOneDeviceDetectionModule(ModuleConfig moduleConfig, DeviceDetector deviceDetector) {
        final Set<? extends Hook<?, ? extends InvocationContext>> hooks = Stream.of(
                new FiftyOneDeviceDetectionEntrypointHook(),
                new FiftyOneDeviceDetectionRawAuctionRequestHook(
                        moduleConfig.getAccountFilter(),
                        new DevicePatchPlannerImp(),
                        deviceDetector,
                        new DeviceInfoPatcherImp<>(DeviceMirror.BUILDER_METHOD_SET::makeAdapter))
        ).collect(Collectors.toSet());

        return new FiftyOneDeviceDetectionModule(hooks);
    }
}
