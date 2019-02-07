package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.rhythmone.RhythmoneBidder;
import org.prebid.server.bidder.rhythmone.RhythmoneUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/rhythmone.yaml", factory = YamlPropertySourceFactory.class)
public class RhythmoneConfiguration {

    private static final String BIDDER_NAME = "rhythmone";

    @Autowired
    @Qualifier("rhythmoneConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("rhythmoneConfigurationProperties")
    @ConfigurationProperties("adapters.rhythmone")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps rhythmOneBidderDeps() {
        final Usersyncer usersyncer = new RhythmoneUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                        metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                        metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new RhythmoneBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
