package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.ttx.TtxBidder;
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
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Configuration
@PropertySource(value = "classpath:/bidder-config/ttx.yaml", factory = YamlPropertySourceFactory.class)
public class TtxConfiguration {

    private static final String BIDDER_NAME = "ttx";

    @Autowired
    @Qualifier("ttxConfigurationProperties")
    private TtxConfigurationProperties configProperties;

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Bean("ttxConfigurationProperties")
    @ConfigurationProperties("adapters.ttx")
    TtxConfigurationProperties configurationProperties() {
        return new TtxConfigurationProperties();
    }

    @Bean
    BidderDeps ttxBidderDeps() {
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
                .bidderCreator(() -> new TtxBidder(configProperties.getEndpoint()))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class TtxConfigurationProperties extends BidderConfigurationProperties {

        @NotNull
        private String partnerId;
    }
}
