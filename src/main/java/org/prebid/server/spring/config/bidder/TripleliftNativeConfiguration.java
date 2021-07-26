package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.tripleliftnative.TripleliftNativeBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.CommonBidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/tripleliftnative.yaml", factory = YamlPropertySourceFactory.class)
public class TripleliftNativeConfiguration {

    private static final String BIDDER_NAME = "triplelift_native";

    @Autowired
    private JacksonMapper mapper;

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Autowired
    private CommonBidderConfigurationProperties commonBidderConfigurationProperties;

    @Autowired
    @Qualifier("tripleliftNativeConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Bean("tripleliftNativeConfigurationProperties")
    @ConfigurationProperties("adapters.tripleliftnative")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps tripleliftNativeBidderDeps() {
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new TripleliftNativeBidder(
                        config.getEndpoint(),
                        config.getExtraInfo(),
                        mapper))
                .assemble();
    }
}
