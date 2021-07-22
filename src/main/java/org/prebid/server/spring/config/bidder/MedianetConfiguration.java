package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.medianet.MedianetBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.prebid.server.util.HttpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/medianet.yaml", factory = YamlPropertySourceFactory.class)
public class MedianetConfiguration {

    private static final String BIDDER_NAME = "medianet";
    private static final String EXTERNAL_URL_MACRO = "{{PREBID_SERVER_ENDPOINT}}";

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Autowired
    private JacksonMapper mapper;

    @Autowired
    @Qualifier("medianetConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Bean("medianetConfigurationProperties")
    @ConfigurationProperties("adapters.medianet")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps medianetBidderDeps() {
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(this::getMedianetBidder)
                .assemble();
    }

    private MedianetBidder getMedianetBidder(BidderConfigurationProperties config) {
        String configEndpoint = config.getEndpoint();
        String encodedExternalUrl = HttpUtil.encodeUrl(externalUrl);
        String bidderEndpoint = configEndpoint.replace(EXTERNAL_URL_MACRO, encodedExternalUrl);
        return new MedianetBidder(bidderEndpoint, mapper);
    }
}
