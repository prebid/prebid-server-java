package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.revcontent.RevcontentBidder;
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
@PropertySource(value = "classpath:/bidder-config/revcontent.yaml", factory = YamlPropertySourceFactory.class)
public class RevcontentConfiguration {

    private static final String BIDDER_NAME = "revcontent";

    @Bean("revcontentConfigurationProperties")
    @ConfigurationProperties("adapters.revcontent")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps revcontentBidderDeps(BidderConfigurationProperties revcontentConfigurationProperties,
                                    @NotBlank @Value("${external-url}") String externalUrl,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(revcontentConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new RevcontentBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
