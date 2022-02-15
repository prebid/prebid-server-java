package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.vidoomy.VidoomyBidder;
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
@PropertySource(value = "classpath:/bidder-config/vidoomy.yaml", factory = YamlPropertySourceFactory.class)
public class VidoomyConfiguration {

    private static final String BIDDER_NAME = "vidoomy";

    @Bean("vidoomyConfigurationProperties")
    @ConfigurationProperties("adapters.vidoomy")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps vidoomyBidderDeps(BidderConfigurationProperties vidoomyConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(vidoomyConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new VidoomyBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
