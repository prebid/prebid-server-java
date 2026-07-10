package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.lemmadigital.LemmaDigitalBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/lemmadigital.yaml", factory = YamlPropertySourceFactory.class)
public class LemmaDigitalConfiguration {

    private static final String BIDDER_NAME = "lemmadigital";

    @Bean("lemmaDigitalConfigurationProperties")
    @ConfigurationProperties("adapters.lemmadigital")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps lemmadigitalBidderDeps(BidderConfigurationProperties lemmaDigitalConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(lemmaDigitalConfigurationProperties)
                .bidderCreator(config -> new LemmaDigitalBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
