package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.imds.ImdsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.prebid.server.version.PrebidVersionProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/imds.yaml", factory = YamlPropertySourceFactory.class)
public class ImdsConfiguration {

    private static final String BIDDER_NAME = "imds";

    @Bean("imdsConfigurationProperties")
    @ConfigurationProperties("adapters.imds")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps imdsBidderDeps(BidderConfigurationProperties imdsConfigurationProperties,
                              PrebidVersionProvider prebidVersionProvider,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(imdsConfigurationProperties)
                .bidderCreator(config -> new ImdsBidder(
                        config.getEndpoint(),
                        prebidVersionProvider.getNameVersionRecord(),
                        mapper)
                )
                .assemble();
    }
}
