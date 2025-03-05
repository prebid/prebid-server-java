package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import com.scientiamobile.wurfl.core.WURFLEngine;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.WURFLEngineInitializer;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionEntrypointHook;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionModule;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + WURFLDeviceDetectionModule.CODE, name = "enabled", havingValue = "true")
@Configuration
@PropertySource(
        value = "classpath:/module-config/WURFL-devicedetection.yaml",
        factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties(WURFLDeviceDetectionConfigProperties.class)
public class WURFLDeviceDetectionConfiguration {

    @Bean
    public WURFLDeviceDetectionModule wurflDeviceDetectionModule(WURFLDeviceDetectionConfigProperties
                                                                         configProperties) {

        final WURFLEngine wurflEngine = WURFLEngineInitializer.builder()
                .configProperties(configProperties)
                .build().initWURFLEngine();
        wurflEngine.load();

        return new WURFLDeviceDetectionModule(List.of(new WURFLDeviceDetectionEntrypointHook(),
                new WURFLDeviceDetectionRawAuctionRequestHook(wurflEngine, configProperties)));
    }
}
