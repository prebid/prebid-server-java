package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.pubmatic.PubmaticAdapter;
import org.prebid.server.bidder.pubmatic.PubmaticBidder;
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
@PropertySource(value = "classpath:/bidder-config/pubmatic.yaml", factory = YamlPropertySourceFactory.class)
public class PubmaticConfiguration {

    private static final String BIDDER_NAME = "pubmatic";

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Autowired
    @Qualifier("pubmaticConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Bean("pubmaticConfigurationProperties")
    @ConfigurationProperties("adapters.pubmatic")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps pubmaticBidderDeps() {
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        final BidderInfo bidderInfo = BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr());

        final UsersyncConfigurationProperties usersync = configProperties.getUsersync();

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(bidderInfo)
                .usersyncerCreator(() -> new Usersyncer(usersync.getCookieFamilyName(), usersync.getUrl(),
                        usersync.getRedirectUrl(), externalUrl, usersync.getType(), usersync.getSupportCors()))
                .bidderCreator(() -> new PubmaticBidder(configProperties.getEndpoint()))
                .adapterCreator(() -> new PubmaticAdapter(usersync.getCookieFamilyName(),
                        configProperties.getEndpoint()))
                .assemble();
    }
}
