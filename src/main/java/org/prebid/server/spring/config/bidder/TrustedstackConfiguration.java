package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.trustedstack.TrustedstackBidder;
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

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/trustedstack.yaml", factory = YamlPropertySourceFactory.class)
public class TrustedstackConfiguration {

    private static final String BIDDER_NAME = "trustedstack";
    private static final String EXTERNAL_URL_MACRO = "{{PREBID_SERVER_ENDPOINT}}";

    @Bean("trustedstackConfigurationProperties")
    @ConfigurationProperties("adapters.trustedstack")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps trustedstackBidderDeps(BidderConfigurationProperties trustedstackConfigurationProperties,
                                      @NotBlank @Value("${external-url}") String externalUrl,
                                      JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(trustedstackConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config ->
                        new TrustedstackBidder(resolveEndpoint(config.getEndpoint(), externalUrl), mapper))
                .assemble();
    }

    private String resolveEndpoint(String configEndpoint, String externalUrl) {
        return configEndpoint.replace(EXTERNAL_URL_MACRO, HttpUtil.encodeUrl(externalUrl));
    }
}
