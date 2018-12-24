package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.adkerneladn.AdkernelAdnBidder;
import org.prebid.server.bidder.adkerneladn.AdkernelAdnMetaInfo;
import org.prebid.server.bidder.adkerneladn.AdkernelAdnUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/adkerneladn.yaml", factory = YamlPropertySourceFactory.class)
public class AdkernelAdnConfiguration {

    private static final String BIDDER_NAME = "adkernelAdn";

    @Autowired
    @Qualifier("adkerneladnConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("adkerneladnConfigurationProperties")
    @ConfigurationProperties("adapters.adkerneladn")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adkernelAdnBidderDeps() {
        final Usersyncer usersyncer = new AdkernelAdnUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .metaInfo(new AdkernelAdnMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new AdkernelAdnBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
