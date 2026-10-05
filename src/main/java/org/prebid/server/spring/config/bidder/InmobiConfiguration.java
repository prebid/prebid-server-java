package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.inmobi.InmobiBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/inmobi.yaml", factory = YamlPropertySourceFactory.class)
public class InmobiConfiguration {

    private static final String BIDDER_NAME = "inmobi";

    @Bean("inmobiConfigurationProperties")
    @ConfigurationProperties("adapters.inmobi")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps inmobiBidderDeps(BidderConfigurationProperties inmobiConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(inmobiConfigurationProperties)
                .bidderCreator(config -> new InmobiBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
