package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.openweb.OpenWebBidder;
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
@PropertySource(value = "classpath:/bidder-config/openweb.yaml", factory = YamlPropertySourceFactory.class)
public class OpenWebConfiguration {

    private static final String BIDDER_NAME = "openweb";

    @Bean("openWebConfigurationProperties")
    @ConfigurationProperties("adapters.openweb")
    BidderConfigurationProperties openWebProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps openWebBidderDeps(BidderConfigurationProperties openWebConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(openWebConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new OpenWebBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
