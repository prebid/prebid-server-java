package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.nativo.NativoBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/nativo.yaml", factory = YamlPropertySourceFactory.class)
public class NativoConfiguration {

    private static final String BIDDER_NAME = "nativo";

    @Bean("nativoConfigurationProperties")
    @ConfigurationProperties("adapters.nativo")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps nativoBidderDeps(BidderConfigurationProperties nativoConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(nativoConfigurationProperties)
                .bidderCreator(config -> new NativoBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
