package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.ogury.OguryBidder;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/ogury.yaml", factory = YamlPropertySourceFactory.class)
public class OguryConfiguration {

    private static final String BIDDER_NAME = "ogury";

    @Bean("oguryConfigurationProperties")
    @ConfigurationProperties("adapters.ogury")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps oguryBidderDeps(BidderConfigurationProperties oguryConfigurationProperties,
                                  CurrencyConversionService currencyConversionService,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(oguryConfigurationProperties)
                .bidderCreator(config -> new OguryBidder(config.getEndpoint(), currencyConversionService, mapper))
                .assemble();
    }
}
