package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.tpmn.TpmnBidder;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/tpmn.yaml", factory = YamlPropertySourceFactory.class)
public class TpmnAdnBidderConfiguration {

    private static final String BIDDER_NAME = "tpmn";

    @Bean("tpmnConfigurationProperties")
    @ConfigurationProperties("adapters.tpmn")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps tpmnBidderDeps(BidderConfigurationProperties tpmnConfigurationProperties,
                                 CurrencyConversionService currencyConversionService,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(tpmnConfigurationProperties)
                .bidderCreator(config -> new TpmnBidder(config.getEndpoint(), currencyConversionService, mapper))
                .assemble();
    }
}
