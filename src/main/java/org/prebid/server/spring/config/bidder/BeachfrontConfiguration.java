package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.beachfront.BeachfrontBidder;
import org.prebid.server.bidder.beachfront.BeachfrontMetaInfo;
import org.prebid.server.bidder.beachfront.BeachfrontUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/beachfront.yaml", factory = YamlPropertySourceFactory.class)
public class BeachfrontConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "beachfront";

    @Autowired
    @Qualifier("beachfrontConfigurationProperties")
    private BeachfrontConfigurationProperties configProperties;

    @Bean("beachfrontConfigurationProperties")
    @ConfigurationProperties("adapters.beachfront")
    BeachfrontConfigurationProperties configurationProperties() {
        return new BeachfrontConfigurationProperties();
    }

    @Bean
    BidderDeps beachfrontBidderDeps() {
        return bidderDeps();
    }

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Override
    protected List<String> deprecatedNames() {
        return configProperties.getDeprecatedNames();
    }

    @Override
    protected List<String> aliases() {
        return configProperties.getAliases();
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new BeachfrontMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr());
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new BeachfrontUsersyncer(configProperties.getUsersyncUrl(), configProperties.getPlatformId());
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new BeachfrontBidder(configProperties.getBannerEndpoint(), configProperties.getVideoEndpoint());
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return null;
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class BeachfrontConfigurationProperties {

        @NotNull
        private Boolean enabled;

        @NotBlank
        private String bannerEndpoint;

        @NotBlank
        private String videoEndpoint;

        @NotBlank
        private String platformId;

        @NotBlank
        private String usersyncUrl;

        @NotNull
        private Boolean pbsEnforcesGdpr;

        @NotNull
        private List<String> deprecatedNames;

        @NotNull
        private List<String> aliases;
    }
}
