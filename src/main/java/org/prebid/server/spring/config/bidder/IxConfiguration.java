package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.ix.IxAdapter;
import org.prebid.server.bidder.ix.IxBidder;
import org.prebid.server.bidder.ix.IxMetaInfo;
import org.prebid.server.bidder.ix.IxUsersyncer;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@Configuration
@PropertySource(value = "classpath:/bidder-config/ix.yaml", factory = YamlPropertySourceFactory.class)
public class IxConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "ix";

    @Value("${adapters.ix.enabled}")
    private boolean enabled;

    @Value("${adapters.ix.endpoint:#{null}}")
    private String endpoint;

    @Value("${adapters.ix.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.ix.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${adapters.ix.deprecated-names}")
    private List<String> deprecatedNames;

    @Value("${adapters.ix.aliases}")
    private List<String> aliases;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    BidderDeps ixBidderDeps() {
        if (enabled && endpoint == null) {
            throw new IllegalStateException(
                    String.format("%s is enabled but has missing required configuration properties. "
                            + "Please review configuration.", BIDDER_NAME));
        }
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
        return new IxMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new IxUsersyncer(usersyncUrl, externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new IxBidder(endpoint);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new IxAdapter(usersyncer, endpoint);
    }

}
