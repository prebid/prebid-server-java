package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.beachfront.BeachfrontBidder;
import org.prebid.server.bidder.beachfront.BeachfrontMetaInfo;
import org.prebid.server.bidder.beachfront.BeachfrontUsersyncer;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@Configuration
@PropertySource(value = "classpath:/bidder-config/beachfront.yaml", factory = YamlPropertySourceFactory.class)
public class BeachfrontConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "beachfront";

    @Value("${adapters.beachfront.enabled}")
    private boolean enabled;

    @Value("${adapters.beachfront.banner-endpoint}")
    private String bannerEndpoint;

    @Value("${adapters.beachfront.video-endpoint}")
    private String videoEndpoint;

    @Value("${adapters.beachfront.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.beachfront.platform-id}")
    private String platformId;

    @Value("${adapters.beachfront.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${adapters.beachfront.deprecated-names}")
    private List<String> deprecatedNames;

    @Value("${adapters.beachfront.aliases}")
    private List<String> aliases;

    @Bean
    BidderDeps beachfrontBidderDeps() {
        return bidderDeps();
    }

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Override
    protected List<String> deprecatedNames() {
        return deprecatedNames;
    }

    @Override
    protected List<String> aliases() {
        return aliases;
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new BeachfrontMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new BeachfrontUsersyncer(usersyncUrl, platformId);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new BeachfrontBidder(bannerEndpoint, videoEndpoint);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return null;
    }

}
