package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.visiblemeasures.VisibleMeasuresBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/visiblemeasures.yaml", factory = YamlPropertySourceFactory.class)
public class VisibleMeasuresConfiguration {

    private static final String BIDDER_NAME = "visiblemeasures";

    @Bean("visiblemeasuresConfigurationProperties")
    @ConfigurationProperties("adapters.visiblemeasures")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps visiblemeasuresBidderDeps(BidderConfigurationProperties visiblemeasuresConfigurationProperties,
                                         JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(visiblemeasuresConfigurationProperties)
                .bidderCreator(config -> new VisibleMeasuresBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
