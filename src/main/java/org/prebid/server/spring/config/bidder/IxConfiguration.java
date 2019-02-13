package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.ix.IxAdapter;
import org.prebid.server.bidder.ix.IxBidder;
import org.prebid.server.bidder.ix.IxUsersyncer;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;
import org.prebid.server.spring.config.bidder.model.UserSyncConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/ix.yaml", factory = YamlPropertySourceFactory.class)
public class IxConfiguration {

    private static final String BIDDER_NAME = "ix";

    @Autowired
    @Qualifier("ixConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("ixConfigurationProperties")
    @ConfigurationProperties("adapters.ix")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps ixBidderDeps() {
        final UserSyncConfigurationProperties userSyncProperties = configProperties.getUsersync();
        final Usersyncer usersyncer = new IxUsersyncer(userSyncProperties.getUrl(),
                userSyncProperties.getType(), userSyncProperties.getSupportCors(), externalUrl);
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                        metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                        metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new IxBidder(configProperties.getEndpoint()))
                .adapterCreator(() -> new IxAdapter(usersyncer, configProperties.getEndpoint()))
                .assemble();
    }
}
