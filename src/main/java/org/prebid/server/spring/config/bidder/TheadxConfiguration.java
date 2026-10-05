package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.theadx.TheadxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/theadx.yaml", factory = YamlPropertySourceFactory.class)
public class TheadxConfiguration {

    private static final String BIDDER_NAME = "theadx";

    @Bean("theadxConfigurationProperties")
    @ConfigurationProperties("adapters.theadx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps theadxBidderDeps(BidderConfigurationProperties theadxConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(theadxConfigurationProperties)
                .bidderCreator(config -> new TheadxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
