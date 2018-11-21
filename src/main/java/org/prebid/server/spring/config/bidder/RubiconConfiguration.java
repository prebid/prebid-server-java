package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.rubicon.RubiconAdapter;
import org.prebid.server.bidder.rubicon.RubiconBidder;
import org.prebid.server.bidder.rubicon.RubiconMetaInfo;
import org.prebid.server.bidder.rubicon.RubiconUsersyncer;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RubiconConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "rubicon";

    @Value("${adapters.rubicon.enabled}")
    private boolean enabled;

    @Value("${adapters.rubicon.endpoint}")
    private String endpoint;

    @Value("${adapters.rubicon.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.rubicon.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${adapters.rubicon.XAPI.Username}")
    private String username;

    @Value("${adapters.rubicon.XAPI.Password}")
    private String password;

    @Value("${adapters.rubicon.deprecated-names}")
    private List<String> deprecatedNames;

    @Value("${adapters.rubicon.aliases}")
    private List<String> aliases;

    @Bean
    BidderDeps rubiconBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        return bidderDeps(httpClient, httpAdapterConnector);
    }

    @Override
    public String bidderName() {
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
    public MetaInfo createMetaInfo() {
        return new RubiconMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    public Usersyncer createUsersyncer() {
        return new RubiconUsersyncer(usersyncUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new RubiconBidder(endpoint, username, password, metaInfo);
    }

    @Override
    public Adapter createAdapter(Usersyncer usersyncer) {
        return new RubiconAdapter(usersyncer, endpoint, username, password);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
