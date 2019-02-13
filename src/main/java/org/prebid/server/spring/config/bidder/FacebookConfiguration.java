package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.facebook.FacebookAdapter;
import org.prebid.server.bidder.facebook.FacebookBidder;
import org.prebid.server.bidder.facebook.FacebookUsersyncer;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.MetaInfo;
import org.prebid.server.spring.config.bidder.model.UserSyncConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Configuration
@PropertySource(value = "classpath:/bidder-config/facebook.yaml", factory = YamlPropertySourceFactory.class)
public class FacebookConfiguration {

    private static final String BIDDER_NAME = "audienceNetwork";

    @Autowired
    @Qualifier("facebookConfigurationProperties")
    private FacebookConfigurationProperties configProperties;

    @Bean("facebookConfigurationProperties")
    @ConfigurationProperties("adapters.facebook")
    FacebookConfigurationProperties configurationProperties() {
        return new FacebookConfigurationProperties();
    }

    @Bean
    BidderDeps facebookBidderDeps() {
        final UserSyncConfigurationProperties userSyncProperties = configProperties.getUsersync();
        final Usersyncer usersyncer = new FacebookUsersyncer(userSyncProperties.getUrl(),
                userSyncProperties.getType(), userSyncProperties.getSupportCors());
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .enabled(configProperties.getEnabled())
                .deprecatedNames(configProperties.getDeprecatedNames())
                .aliases(configProperties.getAliases())
                .bidderInfo(BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                        metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                        metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new FacebookBidder(configProperties.getEndpoint(),
                        configProperties.getNonSecureEndpoint(), configProperties.getPlatformId()))
                .adapterCreator(() -> new FacebookAdapter(usersyncer, configProperties.getEndpoint(),
                        configProperties.getNonSecureEndpoint(), configProperties.getPlatformId()))
                .assemble();
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class FacebookConfigurationProperties {

        @NotNull
        private Boolean enabled;

        @NotBlank
        private String endpoint;

        @NotNull
        private String nonSecureEndpoint;

        @NotNull
        private String platformId;

        @NotNull
        private String usersyncUrl;

        @NotNull
        private Boolean pbsEnforcesGdpr;

        @NotNull
        private List<String> deprecatedNames;

        @NotNull
        private List<String> aliases;

        @NotNull
        private MetaInfo metaInfo;

        @NotNull
        private UserSyncConfigurationProperties usersync;

    }
}
