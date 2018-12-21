package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.openx.OpenxBidder;
import org.prebid.server.bidder.openx.OpenxMetaInfo;
import org.prebid.server.bidder.openx.OpenxUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/openx.yaml", factory = YamlPropertySourceFactory.class)
public class OpenxConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "openx";

    @Autowired
    @Qualifier("openxConfigurationProperties")
    private BidderConfigurationProperties openxProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("openxConfigurationProperties")
    @ConfigurationProperties("adapters.openx")
    BidderConfigurationProperties openxProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps openxBidderDeps() {
        return bidderDeps();
    }

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Override
    protected List<String> deprecatedNames() {
        return openxProperties.getDeprecatedNames();
    }

    @Override
    protected List<String> aliases() {
        return openxProperties.getAliases();
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new OpenxMetaInfo(openxProperties.getEnabled(), openxProperties.getPbsEnforcesGdpr());
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new OpenxUsersyncer(openxProperties.getUsersyncUrl(), externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new OpenxBidder(openxProperties.getEndpoint());
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return null;
    }

}
