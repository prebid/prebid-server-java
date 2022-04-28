package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.BasicPriceFloorAdjuster;
import org.prebid.server.floors.BasicPriceFloorEnforcer;
import org.prebid.server.floors.BasicPriceFloorProcessor;
import org.prebid.server.floors.BasicPriceFloorResolver;
import org.prebid.server.floors.PriceFloorAdjuster;
import org.prebid.server.floors.PriceFloorEnforcer;
import org.prebid.server.floors.PriceFloorFetcher;
import org.prebid.server.floors.PriceFloorProcessor;
import org.prebid.server.floors.PriceFloorResolver;
import org.prebid.server.floors.model.PriceFloorDebugProperties;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PriceFloorsConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    PriceFloorFetcher priceFloorFetcher(
            ApplicationSettings applicationSettings,
            Metrics metrics,
            Vertx vertx,
            TimeoutFactory timeoutFactory,
            HttpClient httpClient,
            PriceFloorDebugProperties debugProperties,
            JacksonMapper mapper) {

        return new PriceFloorFetcher(
                applicationSettings,
                metrics,
                vertx,
                timeoutFactory,
                httpClient,
                debugProperties,
                mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    PriceFloorEnforcer basicPriceFloorEnforcer(CurrencyConversionService currencyConversionService,
                                               Metrics metrics) {
        return new BasicPriceFloorEnforcer(currencyConversionService, metrics);
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "false", matchIfMissing = true)
    PriceFloorEnforcer noOpPriceFloorEnforcer() {
        return PriceFloorEnforcer.noOp();
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    PriceFloorResolver basicPriceFloorResolver(CurrencyConversionService currencyConversionService,
                                               CountryCodeMapper countryCodeMapper,
                                               Metrics metrics) {

        return new BasicPriceFloorResolver(currencyConversionService, countryCodeMapper, metrics);
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "false", matchIfMissing = true)
    PriceFloorResolver noOpPriceFloorResolver() {
        return PriceFloorResolver.noOp();
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    PriceFloorProcessor basicPriceFloorProcessor(PriceFloorFetcher floorFetcher,
                                                 PriceFloorResolver floorResolver,
                                                 JacksonMapper mapper) {

        return new BasicPriceFloorProcessor(floorFetcher, floorResolver, mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "false", matchIfMissing = true)
    PriceFloorProcessor noOpPriceFloorProcessor() {
        return PriceFloorProcessor.noOp();
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    PriceFloorAdjuster basicPriceFloorAdjuster() {
        return new BasicPriceFloorAdjuster();
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "false", matchIfMissing = true)
    PriceFloorAdjuster noOpPriceFloorAdjuster() {
        return PriceFloorAdjuster.noOp();
    }

    @Bean
    @ConfigurationProperties(prefix = "price-floors")
    PriceFloorDebugProperties priceFloorDebugProperties() {
        return new PriceFloorDebugProperties();
    }
}
