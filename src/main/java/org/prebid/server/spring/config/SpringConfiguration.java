package org.prebid.server.spring.config;

import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.Collections;

@Configuration
public class SpringConfiguration {

    @Bean
    ConversionService conversionService() {
        final DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(String.class, OrtbVersion.class, OrtbVersion::fromString);
        return conversionService;
    }

    @Bean
    static CustomScopeConfigurer customScopeConfigurer() {
        final CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.setScopes(Collections.singletonMap(VertxContextScope.NAME, new VertxContextScope()));
        return configurer;
    }
}
