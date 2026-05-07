package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.yandex.YandexBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/yandex.yaml", factory = YamlPropertySourceFactory.class)
public class YandexConfiguration {

    private static final String BIDDER_NAME = "yandex";

    @Bean("yandexConfigurationProperties")
    @ConfigurationProperties("adapters.yandex")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps yandexBidderDeps(BidderConfigurationProperties yandexConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(yandexConfigurationProperties)
                .bidderCreator(config -> new YandexBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
