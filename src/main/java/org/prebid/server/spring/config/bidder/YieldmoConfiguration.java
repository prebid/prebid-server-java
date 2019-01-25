package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.yieldmo.YieldmoBidder;
import org.prebid.server.bidder.yieldmo.YieldmoMetaInfo;
import org.prebid.server.bidder.yieldmo.YieldmoUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/yieldmo.yaml", factory = YamlPropertySourceFactory.class)
public class YieldmoConfiguration {

    private static final String BIDDER_NAME = "yieldmo";

    @Autowired
    @Qualifier("yieldmoConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("yieldmoConfigurationProperties")
    @ConfigurationProperties("adapters.yieldmo")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps yieldmoBidderDeps() {
        final Usersyncer usersyncer = new YieldmoUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .metaInfo(new YieldmoMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new YieldmoBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
