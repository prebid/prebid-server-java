package org.prebid.server.spring.config.bidder;

import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.yahooads.YahooAdsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import jakarta.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/yahooAds.yaml", factory = YamlPropertySourceFactory.class)
public class YahooAdsConfiguration {

    private static final String BIDDER_NAME = "yahooAds";

    @Bean("yahooAdsConfigurationProperties")
    @ConfigurationProperties("adapters.yahooads")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps yahooAdsBidderDeps(BidderConfigurationProperties yahooAdsConfigurationProperties,
                                  @NotBlank @Value("${external-url}") String externalUrl,
                                  JacksonMapper mapper,
                                  BidRequestOrtbVersionConversionManager conversionManager) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(yahooAdsConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new YahooAdsBidder(config.getEndpoint(), conversionManager, mapper))
                .assemble();
    }
}
