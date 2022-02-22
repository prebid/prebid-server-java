package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.tappx.TappxBidder;
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

import javax.validation.constraints.NotBlank;
import java.time.Clock;

@Configuration
@PropertySource(value = "classpath:/bidder-config/tappx.yaml", factory = YamlPropertySourceFactory.class)
public class TappxConfiguration {

    private static final String BIDDER_NAME = "tappx";

    @Bean("tappxConfigurationProperties")
    @ConfigurationProperties("adapters.tappx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps tappxBidderDeps(BidderConfigurationProperties tappxConfigurationProperties,
                               @NotBlank @Value("${external-url}") String externalUrl,
                               Clock clock,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(tappxConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new TappxBidder(config.getEndpoint(), clock, mapper))
                .assemble();
    }
}

