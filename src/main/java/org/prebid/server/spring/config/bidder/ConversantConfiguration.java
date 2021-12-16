package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.conversant.ConversantBidder;
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
import javax.validation.constraints.NotNull;

@Configuration
@PropertySource(value = "classpath:/bidder-config/conversant.yaml", factory = YamlPropertySourceFactory.class)
public class ConversantConfiguration {

    private static final String BIDDER_NAME = "conversant";

    @Bean("conversantConfigurationProperties")
    @ConfigurationProperties("adapters.conversant")
    ConversantConfigurationProperties configurationProperties() {
        return new ConversantConfigurationProperties();
    }

    @Bean
    BidderDeps conversantBidderDeps(ConversantConfigurationProperties conversantConfigurationProperties,
                                    @NotBlank @Value("${external-url}") String externalUrl,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(conversantConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config ->
                        new ConversantBidder(
                                config.getEndpoint(),
                                conversantConfigurationProperties.getGenerateBidId(),
                                mapper))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class ConversantConfigurationProperties extends BidderConfigurationProperties {

        @NotNull
        private Boolean generateBidId;
    }
}
