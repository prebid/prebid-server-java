package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.adform.AdformAdapter;
import org.prebid.server.bidder.adform.AdformBidder;
import org.prebid.server.bidder.adform.AdformMetaInfo;
import org.prebid.server.bidder.adform.AdformUsersyncer;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adform.yaml", factory = YamlPropertySourceFactory.class)
public class AdformConfiguration {

    private static final String BIDDER_NAME = "adform";

    @Autowired
    @Qualifier("adformConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("adformConfigurationProperties")
    @ConfigurationProperties("adapters.adform")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adformBidderDeps() {
        final Usersyncer usersyncer = new AdformUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .metaInfo(new AdformMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new AdformBidder(configProperties.getEndpoint()))
                .adapterCreator(() -> new AdformAdapter(usersyncer, configProperties.getEndpoint()))
                .assemble();
    }
}
