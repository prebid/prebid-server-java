package org.prebid.server.spring.config;

import org.prebid.server.json.ObjectMapperConfigurer;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ApplicationConfiguration {

    static {
        ObjectMapperConfigurer.configure();
    }

    @Bean
    ConversionService conversionService() {
        return new DefaultConversionService();
    }

    @Bean
    static CustomScopeConfigurer customScopeConfigurer() {
        final CustomScopeConfigurer configurer = new CustomScopeConfigurer();

        final Map<String, Object> scopes = new HashMap<>();
        scopes.put(VertxContextScope.NAME, new VertxContextScope());

        configurer.setScopes(scopes);
        return configurer;
    }
}
