package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.openx.OpenxBidder;
import org.prebid.server.bidder.openx.OpenxMetaInfo;
import org.prebid.server.bidder.openx.OpenxUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/openx.yaml", factory = YamlPropertySourceFactory.class)
public class OpenxConfiguration {

    private static final String BIDDER_NAME = "openx";

    @Autowired
    @Qualifier("openxConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("openxConfigurationProperties")
    @ConfigurationProperties("adapters.openx")
    BidderConfigurationProperties openxProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps openxBidderDeps() {
        final Usersyncer usersyncer = new OpenxUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .metaInfo(new OpenxMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new OpenxBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
