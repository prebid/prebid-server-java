package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.rubicon.RubiconAdapter;
import org.prebid.server.bidder.rubicon.RubiconBidder;
import org.prebid.server.bidder.rubicon.RubiconMetaInfo;
import org.prebid.server.bidder.rubicon.RubiconUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/rubicon.yaml", factory = YamlPropertySourceFactory.class)
public class RubiconConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "rubicon";

    @Autowired
    @Qualifier("rubiconConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${adapters.rubicon.XAPI.Username}")
    private String username;

    @Value("${adapters.rubicon.XAPI.Password}")
    private String password;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("rubiconConfigurationProperties")
    @ConfigurationProperties("adapters.rubicon")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps rubiconBidderDeps() {
        return bidderDeps();
    }

    @Override
    public String bidderName() {
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
    public MetaInfo createMetaInfo() {
        return new RubiconMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr());
    }

    @Override
    public Usersyncer createUsersyncer() {
        return new RubiconUsersyncer(configProperties.getUsersyncUrl());
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new RubiconBidder(configProperties.getEndpoint(), username, password, metaInfo);
    }

    @Override
    public Adapter createAdapter(Usersyncer usersyncer) {
        return new RubiconAdapter(usersyncer, configProperties.getEndpoint(), username, password);
    }

}
