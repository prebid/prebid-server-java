package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.sovrn.SovrnAdapter;
import org.prebid.server.bidder.sovrn.SovrnBidder;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;
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
@PropertySource(value = "classpath:/bidder-config/sovrn.yaml", factory = YamlPropertySourceFactory.class)
public class SovrnConfiguration {

    private static final String BIDDER_NAME = "sovrn";

    @Autowired
    @Qualifier("sovrnConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Bean("sovrnConfigurationProperties")
    @ConfigurationProperties("adapters.sovrn")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps sovrnBidderDeps() {
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        final BidderInfo bidderInfo = BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr());

        final UsersyncConfigurationProperties usersyncProperties = configProperties.getUsersync();
        final Usersyncer usersyncer = new Usersyncer(usersyncProperties.getCookieFamilyName(),
                usersyncProperties.getUrl(), usersyncProperties.getRedirectUrl(), externalUrl,
                usersyncProperties.getType(), usersyncProperties.getSupportCors());

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(bidderInfo)
                .usersyncer(usersyncer)
                .bidderCreator(() -> new SovrnBidder(configProperties.getEndpoint()))
                .adapterCreator(() -> new SovrnAdapter(usersyncer, configProperties.getEndpoint()))
                .assemble();
    }
}
