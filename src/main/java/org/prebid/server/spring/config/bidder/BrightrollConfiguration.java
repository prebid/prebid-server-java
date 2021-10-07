package org.prebid.server.spring.config.bidder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.brightroll.BrightrollBidder;
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
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@PropertySource(value = "classpath:/bidder-config/brightroll.yaml", factory = YamlPropertySourceFactory.class)
public class BrightrollConfiguration {

    private static final String BIDDER_NAME = "brightroll";

    @Bean("brightrollConfigurationProperties")
    @ConfigurationProperties("adapters.brightroll")
    BrightrollConfigurationProperties configurationProperties() {
        return new BrightrollConfigurationProperties();
    }

    @Bean
    BidderDeps brightrollBidderDeps(BrightrollConfigurationProperties brightrollConfigurationProperties,
                                    @NotBlank @Value("${external-url}") String externalUrl,
                                    JacksonMapper mapper) {

        final List<BidderAccount> accounts = brightrollConfigurationProperties.getAccounts();
        final Map<String, BigDecimal> publisherIdToBidFloor = CollectionUtils.emptyIfNull(accounts).stream()
                .collect(HashMap::new,
                        (map, account) -> map.put(account.getId(), account.getBidFloor()),
                        HashMap::putAll);

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(brightrollConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config ->
                        new BrightrollBidder(
                                config.getEndpoint(),
                                mapper,
                                publisherIdToBidFloor))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrightrollConfigurationProperties extends BidderConfigurationProperties {

        private List<BidderAccount> accounts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BidderAccount {

        @NotBlank
        private String id;

        private BigDecimal bidFloor;
    }
}
