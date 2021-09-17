package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.openweb.OpenwebBidder;
import org.prebid.server.bidder.openx.OpenxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/openweb.yaml", factory = YamlPropertySourceFactory.class)
public class OpenwebConfiguration {

    private static final String BIDDER_NAME = "openweb";

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Autowired
    private JacksonMapper mapper;

    @Autowired
    @Qualifier("openwebConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Bean("openwebConfigurationProperties")
    @ConfigurationProperties("adapters.openweb")
    BidderConfigurationProperties openwebProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps openwebBidderDeps() {
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new OpenwebBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
