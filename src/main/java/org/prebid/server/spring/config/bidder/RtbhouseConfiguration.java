package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.rtbhouse.RtbhouseBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
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
@PropertySource(value = "classpath:/bidder-config/rtbhouse.yaml", factory = YamlPropertySourceFactory.class)
public class RtbhouseConfiguration {

    private static final String BIDDER_NAME = "rtbhouse";

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Autowired
    private JacksonMapper mapper;

    @Autowired
    @Qualifier("rtbhouseConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Bean("rtbhouseConfigurationProperties")
    @ConfigurationProperties("adapters.rtbhouse")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps rtbhouseBidderDeps() {
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(BidderInfoCreator.create(configProperties))
                .usersyncerCreator(UsersyncerCreator.create(configProperties.getUsersync(), externalUrl))
                .bidderCreator(() -> new RtbhouseBidder(configProperties.getEndpoint(), mapper))
                .assemble();
    }
}
