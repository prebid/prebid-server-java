package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.medianet.MedianetBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.prebid.server.util.HttpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import jakarta.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/medianet.yaml", factory = YamlPropertySourceFactory.class)
public class MedianetConfiguration {

    private static final String BIDDER_NAME = "medianet";
    private static final String EXTERNAL_URL_MACRO = "{{PREBID_SERVER_ENDPOINT}}";

    @Bean("medianetConfigurationProperties")
    @ConfigurationProperties("adapters.medianet")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps medianetBidderDeps(BidderConfigurationProperties medianetConfigurationProperties,
                                  @NotBlank @Value("${external-url}") String externalUrl,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(medianetConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new MedianetBidder(resolveEndpoint(config.getEndpoint(), externalUrl), mapper))
                .assemble();
    }

    private String resolveEndpoint(String configEndpoint, String externalUrl) {
        return configEndpoint.replace(EXTERNAL_URL_MACRO, HttpUtil.encodeUrl(externalUrl));
    }
}
