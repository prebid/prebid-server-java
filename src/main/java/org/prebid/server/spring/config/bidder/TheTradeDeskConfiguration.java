package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.thetradedesk.TheTradeDeskBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/thetradedesk.yaml", factory = YamlPropertySourceFactory.class)
public class TheTradeDeskConfiguration {

    private static final String BIDDER_NAME = "thetradedesk";

    @Bean("thetradedeskConfigurationProperties")
    @ConfigurationProperties("adapters.thetradedesk")
    TheTradeDeskConfigurationProperties configurationProperties() {
        return new TheTradeDeskConfigurationProperties();
    }

    @Bean
    BidderDeps theTradeDeskBidderDeps(TheTradeDeskConfigurationProperties theTradeDeskConfigurationProperties,
                                      JacksonMapper mapper) {

        return BidderDepsAssembler.<TheTradeDeskConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(theTradeDeskConfigurationProperties)
                .bidderCreator(config -> new TheTradeDeskBidder(
                        config.getEndpoint(),
                        mapper,
                        config.getExtraInfo().getSupplyId())
                ).assemble();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class TheTradeDeskConfigurationProperties extends BidderConfigurationProperties {

        private ExtraInfo extraInfo = new ExtraInfo();
    }

    @Data
    @NoArgsConstructor
    private static class ExtraInfo {

        String supplyId;
    }
}
