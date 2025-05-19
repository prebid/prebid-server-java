package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adnuntius.AdnuntiusBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.prebid.server.util.ObjectUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import jakarta.validation.constraints.NotBlank;
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
                                   @NotBlank @Value("${external-url}") String externalUrl,
                                   Clock clock,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.<AdnuntiusConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(adnuntiusConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new AdnuntiusBidder(
                        config.getEndpoint(),
                        ObjectUtil.getIfNotNull(config.getExtraInfo(), ExtraInfo::getUrl),
                        clock,
                        mapper))
                .assemble();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class AdnuntiusConfigurationProperties extends BidderConfigurationProperties {

        private ExtraInfo extraInfo = new ExtraInfo();
    }

    @Data
    @NoArgsConstructor
    private static class ExtraInfo {

        String url;
    }
}
