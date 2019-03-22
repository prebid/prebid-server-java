package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.rubicon.RubiconAdapter;
import org.prebid.server.bidder.rubicon.RubiconBidder;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Configuration
@PropertySource(value = "classpath:/bidder-config/rubicon.yaml", factory = YamlPropertySourceFactory.class)
public class RubiconConfiguration {

    private static final String BIDDER_NAME = "rubicon";

    @Autowired
    @Qualifier("rubiconConfigurationProperties")
    private RubiconConfigurationProperties configProperties;

    @Bean("rubiconConfigurationProperties")
    @ConfigurationProperties("adapters.rubicon")
    RubiconConfigurationProperties configurationProperties() {
        return new RubiconConfigurationProperties();
    }

    @Bean
    BidderDeps rubiconBidderDeps() {
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        final BidderInfo bidderInfo = BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr());

        final UsersyncConfigurationProperties usersync = configProperties.getUsersync();

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(bidderInfo)
                .usersyncerCreator(() -> new Usersyncer(usersync.getCookieFamilyName(), usersync.getUrl(),
                        usersync.getRedirectUrl(), null, usersync.getType(), usersync.getSupportCors()))
                .bidderCreator(() -> new RubiconBidder(configProperties.getEndpoint(),
                        configProperties.getXapi().getUsername(), configProperties.getXapi().getPassword(),
                        metaInfo.getSupportedVendors()))
                .adapterCreator(() -> new RubiconAdapter(usersync.getCookieFamilyName(), configProperties.getEndpoint(),
                        configProperties.getXapi().getUsername(), configProperties.getXapi().getPassword()))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class RubiconConfigurationProperties extends BidderConfigurationProperties {

        @Valid
        @NotNull
        private XAPI xapi = new XAPI();
    }

    @Data
    @NoArgsConstructor
    private static class XAPI {

        @NotNull
        private String username;

        @NotNull
        private String password;
    }
}
