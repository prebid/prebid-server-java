package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.brightroll.BrightrollBidder;
import org.prebid.server.bidder.brightroll.BrightrollMetaInfo;
import org.prebid.server.bidder.brightroll.BrightrollUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/brightroll.yaml", factory = YamlPropertySourceFactory.class)
public class BrightrollConfiguration {

    private static final String BIDDER_NAME = "brightroll";

    @Autowired
    @Qualifier("brightrollConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("brightrollConfigurationProperties")
    @ConfigurationProperties("adapters.brightroll")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps brightrollBidderDeps() {
        final Usersyncer usersyncer = new BrightrollUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .metaInfo(new BrightrollMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new BrightrollBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
