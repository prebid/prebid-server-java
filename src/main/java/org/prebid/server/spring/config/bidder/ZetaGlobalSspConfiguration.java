package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.zeta_global_ssp.ZetaGlobalSspBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import jakarta.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/zeta_global_ssp.yaml", factory = YamlPropertySourceFactory.class)
public class ZetaGlobalSspConfiguration {

    private static final String BIDDER_NAME = "zeta_global_ssp";

    @Bean
    @ConfigurationProperties("adapters.zeta-global-ssp")
    BidderConfigurationProperties zetaGlobalSspConfigurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps zetaGlobalSspBidderDeps(BidderConfigurationProperties zetaGlobalSspConfigurationProperties,
                                       @NotBlank @Value("${external-url}") String externalUrl,
                                       JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(zetaGlobalSspConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new ZetaGlobalSspBidder(config.getEndpoint(), mapper))
                .assemble();
    }

}
