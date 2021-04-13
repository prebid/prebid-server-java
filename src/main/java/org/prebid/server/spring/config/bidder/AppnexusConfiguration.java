package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.appnexus.AppnexusBidder;
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
@PropertySource(value = "classpath:/bidder-config/appnexus.yaml", factory = YamlPropertySourceFactory.class)
public class AppnexusConfiguration {

    private static final String BIDDER_NAME = "appnexus";

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Autowired
    private JacksonMapper mapper;

    @Autowired
    @Qualifier("appnexusConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Bean("appnexusConfigurationProperties")
    @ConfigurationProperties("adapters.appnexus")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps appnexusBidderDeps() {
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new AppnexusBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
