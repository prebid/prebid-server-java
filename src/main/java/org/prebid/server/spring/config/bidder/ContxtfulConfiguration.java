package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.contxtful.ContxtfulBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/contxtful.yaml", factory = YamlPropertySourceFactory.class)
public class ContxtfulConfiguration {

    private static final String BIDDER_NAME = "contxtful";

    @Bean("contxtfulConfigurationProperties")
    @ConfigurationProperties("adapters.contxtful")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps contxtfulBidderDeps(BidderConfigurationProperties contxtfulConfigurationProperties,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(contxtfulConfigurationProperties)
                .bidderCreator(config -> new ContxtfulBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
