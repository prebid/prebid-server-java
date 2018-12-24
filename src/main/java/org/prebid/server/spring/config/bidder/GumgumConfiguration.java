package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.gumgum.GumgumBidder;
import org.prebid.server.bidder.gumgum.GumgumMetaInfo;
import org.prebid.server.bidder.gumgum.GumgumUsersyncer;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/gumgum.yaml", factory = YamlPropertySourceFactory.class)
public class GumgumConfiguration {

    private static final String BIDDER_NAME = "gumgum";

    @Autowired
    @Qualifier("gumgumConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("gumgumConfigurationProperties")
    @ConfigurationProperties("adapters.gumgum")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps gumGumOneBidderDeps() {
        final Usersyncer usersyncer = new GumgumUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .metaInfo(new GumgumMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new GumgumBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
