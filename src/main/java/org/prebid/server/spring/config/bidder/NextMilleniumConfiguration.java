package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.nextmillenium.NextMilleniumBidder;
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

@Configuration
@PropertySource(value = "classpath:/bidder-config/nextmillenium.yaml", factory = YamlPropertySourceFactory.class)
public class NextMilleniumConfiguration {

    private static final String BIDDER_NAME = "nextmillenium";

    @Bean("nextmilleniumConfigurationProperties")
    @ConfigurationProperties("adapters.nextmillenium")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps nextmilleniumBidderDeps(BidderConfigurationProperties nextmilleniumConfigurationProperties,
                                       @NotBlank @Value("${external-url}") String externalUrl,
                                       JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(nextmilleniumConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new NextMilleniumBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
