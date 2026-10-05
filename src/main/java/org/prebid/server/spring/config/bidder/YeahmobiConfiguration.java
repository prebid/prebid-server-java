package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.yeahmobi.YeahmobiBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/yeahmobi.yaml", factory = YamlPropertySourceFactory.class)
public class YeahmobiConfiguration {

    private static final String BIDDER_NAME = "yeahmobi";

    @Bean("yeahmobiConfigurationProperties")
    @ConfigurationProperties("adapters.yeahmobi")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps yeahmobiBidderDeps(BidderConfigurationProperties yeahmobiConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(yeahmobiConfigurationProperties)
                .bidderCreator(config -> new YeahmobiBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
