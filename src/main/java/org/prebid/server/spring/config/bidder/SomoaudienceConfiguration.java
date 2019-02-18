package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.somoaudience.SomoaudienceBidder;
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

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/somoaudience.yaml", factory = YamlPropertySourceFactory.class)
public class SomoaudienceConfiguration {

    private static final String BIDDER_NAME = "somoaudience";

    @Autowired
    @Qualifier("somoaudienceConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Bean("somoaudienceConfigurationProperties")
    @ConfigurationProperties("adapters.somoaudience")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps somoaudienceBidderDeps() {
        final UserSyncConfigurationProperties userSyncProperties = configProperties.getUsersync();
        final Usersyncer usersyncer = Usersyncer.create(userSyncProperties.getCookieFamilyName(),
                userSyncProperties.getUrl(), userSyncProperties.getRedirectUrl(), externalUrl,
                userSyncProperties.getType(), userSyncProperties.getSupportCors());
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                        metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                        metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new SomoaudienceBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
