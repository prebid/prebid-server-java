package org.prebid.server.spring.config;

import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;

@Configuration
public class ConversionServiceConfiguration {

    @Bean
    ConversionService conversionService() {
        final DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(new StringToOrtbVersionConverter());

        return conversionService;
    }

    private static class StringToOrtbVersionConverter implements Converter<String, OrtbVersion> {

        @Override
        public OrtbVersion convert(String source) {
            return OrtbVersion.fromString(source);
        }
    }
}
