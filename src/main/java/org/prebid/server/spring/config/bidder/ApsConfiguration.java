package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.aps.ApsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/aps.yaml", factory = YamlPropertySourceFactory.class)
public class ApsConfiguration {

    private static final String BIDDER_NAME = "aps";

    @Bean("apsConfigurationProperties")
    @ConfigurationProperties("adapters.aps")
    ApsConfigurationProperties configurationProperties() {
        return new ApsConfigurationProperties();
    }

    @Bean
    BidderDeps apsBidderDeps(ApsConfigurationProperties apsConfigurationProperties,
                             JacksonMapper mapper) {

        return BidderDepsAssembler.<ApsConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(apsConfigurationProperties)
                .bidderCreator(config -> new ApsBidder(config.getEndpoint(), mapper))
                .assemble();
    }

    // The APS account is a per-request bidder param (imp.ext.prebid.bidder.aps.accountID), not a
    // host-level setting, so one Prebid Server host can serve multiple publishers. No adapter-specific
    // host config is needed beyond the standard bidder properties.
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class ApsConfigurationProperties extends BidderConfigurationProperties {
    }
}
