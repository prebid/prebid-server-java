package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.MetaInfo;
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

import java.util.List;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adkerneladn.yaml", factory = YamlPropertySourceFactory.class)
public class AdkernelAdnConfiguration extends BidderConfiguration {

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
        return bidderDeps();
    }

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Override
    protected List<String> deprecatedNames() {
        return configProperties.getDeprecatedNames();
    }

    @Override
    protected List<String> aliases() {
        return configProperties.getAliases();
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new AdkernelAdnMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr());
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new AdkernelAdnUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new AdkernelAdnBidder(configProperties.getEndpoint());
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return null;
    }

}
