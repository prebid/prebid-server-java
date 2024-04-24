package org.prebid.server.hooks.modules.fiftyone.devicedetection.config;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.AccountControl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.BidRequestEvidenceCollector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.BidRequestPatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceDetector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlanner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceTypeConverter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EntrypointEvidenceCollector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.ModuleContextPatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineSupplier;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PriorityEvidenceSelector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.UserAgentEvidenceConverter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.AccountControlImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.BidRequestPatcherImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.DeviceDetectorImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.DevicePatchPlannerImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.DevicePatcherImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.DeviceTypeConverterImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.ModuleContextPatcherImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.PriorityEvidenceSelectorImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.UserAgentEvidenceConverterImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.evidencecollectors.BidRequestReader;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.evidencecollectors.EntrypointDataReader;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders.PipelineBuilderSpawnerImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders.PipelinePerformanceConfigurator;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders.PipelineSupplierImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders.PipelineUpdateConfigurator;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionEntrypointHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.ModuleConfig;
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
    DevicePatchPlanner fiftyOneDeviceDetectionDevicePatchPlanner(ModuleConfig moduleConfig) {
        return new DevicePatchPlannerImp();
    }

    @Bean
    EntrypointEvidenceCollector fiftyOneDeviceDetectionEntrypointEvidenceCollector() {
        return new EntrypointDataReader();
    }

    @Bean
    BidRequestEvidenceCollector fiftyOneDeviceDetectionBidRequestEvidenceCollector() {
        return new BidRequestReader();
    }

    @Bean
    ModuleContextPatcher fiftyOneDeviceDetectionModuleContextPatcher() {
        return new ModuleContextPatcherImp();
    }

    @Bean
    PipelineSupplier fiftyOneDeviceDetectionPipelineSupplier(ModuleConfig moduleConfig) throws Exception {
        final var pipelineBuilder = new PipelineBuilderSpawnerImp().makeBuilder(moduleConfig.getDataFile());
        new PipelineUpdateConfigurator().applyProperties(pipelineBuilder, moduleConfig.getDataFile().getUpdate());
        new PipelinePerformanceConfigurator().applyProperties(pipelineBuilder, moduleConfig.getPerformance());
        return new PipelineSupplierImp(pipelineBuilder);
    }

    @Bean
    UserAgentEvidenceConverter fiftyOneDeviceDetectionUserAgentEvidenceConverter() {
        return new UserAgentEvidenceConverterImp();
    }

    @Bean
    PriorityEvidenceSelector fiftyOneDeviceDetectionPriorityEvidenceSelector(
            UserAgentEvidenceConverter userAgentEvidenceConverter)
    {
        return new PriorityEvidenceSelectorImp(userAgentEvidenceConverter);
    }

    @Bean
    DeviceTypeConverter fiftyOneDeviceDetectionDeviceTypeConverter() {
        return new DeviceTypeConverterImp();
    }

    @Bean
    DeviceDetector fiftyOneDeviceDetectionDeviceDetector(
            PipelineSupplier pipelineSupplier,
            PriorityEvidenceSelector priorityEvidenceSelector,
            DeviceTypeConverter deviceTypeConverter,
            DevicePatcher devicePatcher)
    {
        return new DeviceDetectorImp(pipelineSupplier, priorityEvidenceSelector, deviceTypeConverter, devicePatcher);
    }

    @Bean
    DevicePatcher fiftyOneDeviceDetectionDevicePatcher() {
        return new DevicePatcherImp();
    }

    @Bean
    BidRequestPatcher fiftyOneDeviceDetectionBidRequestPatcher(
            DevicePatchPlanner devicePatchPlanner,
            BidRequestEvidenceCollector bidRequestEvidenceCollector,
            DeviceDetector deviceDetector,
            DevicePatcher devicePatcher)
    {
        return new BidRequestPatcherImp(devicePatchPlanner, bidRequestEvidenceCollector, deviceDetector, devicePatcher);
    }

    @Bean
    AccountControl fiftyOneDeviceDetectionAccountControl(ModuleConfig moduleConfig) {
        return new AccountControlImp(moduleConfig.getAccountFilter());
    }

    @Bean
    Module fiftyOneDeviceDetectionModule(
            AccountControl accountControl,
            EntrypointEvidenceCollector entrypointEvidenceCollector,
            BidRequestEvidenceCollector bidRequestEvidenceCollector,
            ModuleContextPatcher moduleContextPatcher,
            BidRequestPatcher bidRequestPatcher)
    {
        final Set<? extends Hook<?, ? extends InvocationContext>> hooks = Stream.of(
                new FiftyOneDeviceDetectionEntrypointHook(entrypointEvidenceCollector, moduleContextPatcher),
                new FiftyOneDeviceDetectionRawAuctionRequestHook(
                        accountControl,
                        bidRequestEvidenceCollector,
                        moduleContextPatcher,
                        bidRequestPatcher)
        ).collect(Collectors.toSet());

        return new FiftyOneDeviceDetectionModule(hooks);
    }
}
