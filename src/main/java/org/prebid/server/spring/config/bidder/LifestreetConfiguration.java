package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.lifestreet.LifestreetAdapter;
import org.prebid.server.bidder.lifestreet.LifestreetBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.BidderInfoCreator;
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
@PropertySource(value = "classpath:/bidder-config/lifestreet.yaml", factory = YamlPropertySourceFactory.class)
public class LifestreetConfiguration {

    private static final String BIDDER_NAME = "lifestreet";

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Autowired
    private JacksonMapper mapper;

    @Autowired
    @Qualifier("lifestreetConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Bean("lifestreetConfigurationProperties")
    @ConfigurationProperties("adapters.lifestreet")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps lifestreetBidderDeps() {
        final UsersyncConfigurationProperties usersync = configProperties.getUsersync();

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(BidderInfoCreator.create(configProperties))
                .usersyncerCreator(UsersyncerCreator.create(usersync, externalUrl))
                .bidderCreator(() -> new LifestreetBidder(configProperties.getEndpoint(), mapper))
                .adapterCreator(() -> new LifestreetAdapter(usersync.getCookieFamilyName(),
                        configProperties.getEndpoint(), mapper))
                .assemble();
    }
}
