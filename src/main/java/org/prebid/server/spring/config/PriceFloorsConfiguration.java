package org.prebid.server.spring.config;

import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.floors.BasicPriceFloorEnforcer;
import org.prebid.server.floors.PriceFloorEnforcer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PriceFloorsConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "true")
    PriceFloorEnforcer basicPriceFloorEnforcer(CurrencyConversionService currencyConversionService) {
        return new BasicPriceFloorEnforcer(currencyConversionService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "price-floors", name = "enabled", havingValue = "false", matchIfMissing = true)
    PriceFloorEnforcer noOpPriceFloorEnforcer() {
        return PriceFloorEnforcer.noOp();
    }
}
