package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.eplanning.EplanningBidder;
import org.prebid.server.bidder.eplanning.EplanningUsersyncer;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/eplanning.yaml", factory = YamlPropertySourceFactory.class)
public class EplanningConfiguration {

    private static final String BIDDER_NAME = "eplanning";

    @Autowired
    @Qualifier("eplanningConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("eplanningConfigurationProperties")
    @ConfigurationProperties("adapters.eplanning")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps eplanningBidderDeps() {
        final Usersyncer usersyncer = new EplanningUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                        metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                        metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new EplanningBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
