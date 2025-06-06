package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import org.prebid.server.bidadjustments.BidAdjustmentsRulesResolver;
import org.prebid.server.bidadjustments.FloorAdjustmentFactorResolver;
import org.prebid.server.bidadjustments.FloorAdjustmentsResolver;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.floors.BasicPriceFloorAdjuster;
import org.prebid.server.floors.BasicPriceFloorEnforcer;
import org.prebid.server.floors.BasicPriceFloorProcessor;
import org.prebid.server.floors.BasicPriceFloorResolver;
import org.prebid.server.floors.NoSignalBidderPriceFloorAdjuster;
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
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
    PriceFloorEnforcer basicPriceFloorEnforcer(CurrencyConversionService currencyConversionService, Metrics metrics) {
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
                                               Metrics metrics,
                                               JacksonMapper mapper) {

        return new BasicPriceFloorResolver(currencyConversionService, countryCodeMapper, metrics, mapper);
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
                                                 Metrics metrics,
                                                 JacksonMapper mapper,
                                                 @Value("${logging.sampling-rate:0.01}") double logSamplingRate) {

        return new BasicPriceFloorProcessor(floorFetcher, floorResolver, metrics, mapper, logSamplingRate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "false", matchIfMissing = true)
    PriceFloorProcessor noOpPriceFloorProcessor() {
        return PriceFloorProcessor.noOp();
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    FloorAdjustmentFactorResolver floorsAdjustmentFactorResolver() {
        return new FloorAdjustmentFactorResolver();
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    FloorAdjustmentsResolver floorAdjustmentsResolver(BidAdjustmentsRulesResolver bidAdjustmentsRulesResolver,
                                                      CurrencyConversionService currencyService) {

        return new FloorAdjustmentsResolver(bidAdjustmentsRulesResolver, currencyService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    BasicPriceFloorAdjuster basicPriceFloorAdjuster(FloorAdjustmentFactorResolver floorAdjustmentFactorResolver,
                                                    FloorAdjustmentsResolver floorAdjustmentsResolver) {

        return new BasicPriceFloorAdjuster(floorAdjustmentFactorResolver, floorAdjustmentsResolver);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    PriceFloorAdjuster noSignalBidderPriceFloorAdjuster(BasicPriceFloorAdjuster basicPriceFloorAdjuster) {
        return new NoSignalBidderPriceFloorAdjuster(basicPriceFloorAdjuster);
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
