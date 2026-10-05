package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adnuntius.AdnuntiusBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.Clock;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adnuntius.yaml", factory = YamlPropertySourceFactory.class)
public class AdnuntiusBidderConfiguration {

    private static final String BIDDER_NAME = "adnuntius";

    @Bean("adnuntiusConfigurationProperties")
    @ConfigurationProperties("adapters.adnuntius")
    AdnuntiusConfigurationProperties configurationProperties() {
        return new AdnuntiusConfigurationProperties();
    }

    @Bean
    BidderDeps adnuntiusBidderDeps(AdnuntiusConfigurationProperties adnuntiusConfigurationProperties,
                                   Clock clock,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.<AdnuntiusConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(adnuntiusConfigurationProperties)
                .bidderCreator(config -> new AdnuntiusBidder(
                        config.getEndpoint(),
                        config.getEuEndpoint(),
                        clock,
                        mapper))
                .assemble();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class AdnuntiusConfigurationProperties extends BidderConfigurationProperties {

        private String euEndpoint;
    }
}
