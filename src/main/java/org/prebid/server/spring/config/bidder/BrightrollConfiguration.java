package org.prebid.server.spring.config.bidder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.brightroll.BrightrollBidder;
import org.prebid.server.bidder.brightroll.model.PublisherOverride;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
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
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@PropertySource(value = "classpath:/bidder-config/brightroll.yaml", factory = YamlPropertySourceFactory.class)
public class BrightrollConfiguration {

    private static final String BIDDER_NAME = "brightroll";

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Autowired
    private JacksonMapper mapper;

    @Autowired
    @Qualifier("brightrollConfigurationProperties")
    private BrightrollConfigurationProperties configProperties;

    @Bean("brightrollConfigurationProperties")
    @ConfigurationProperties("adapters.brightroll")
    BrightrollConfigurationProperties configurationProperties() {
        return new BrightrollConfigurationProperties();
    }

    @Bean
    BidderDeps brightrollBidderDeps() {
        final Map<String, PublisherOverride> publisherIdToOverride = configProperties.getAccounts() == null
                ? Collections.emptyMap()
                : configProperties.getAccounts().stream()
                .collect(Collectors.toMap(BidderAccount::getId, this::toPublisherOverride));
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new BrightrollBidder(
                        config.getEndpoint(),
                        mapper,
                        publisherIdToOverride))
                .assemble();
    }

    private PublisherOverride toPublisherOverride(BidderAccount bidderAccount) {
        return PublisherOverride.of(bidderAccount.getBadv(), bidderAccount.getBcat(), bidderAccount.getImpBattr(),
                bidderAccount.getBidFloor());
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

        private String id;

        /**
         * Blocked advertisers.
         */
        private List<String> badv;

        /**
         * Blocked advertisers.
         */
        private List<String> bcat;

        /**
         * Blocked IAB categories.
         */
        private List<Integer> impBattr;

        /**
         * Request Bid floor.
         */
        private BigDecimal bidFloor;
    }
}
