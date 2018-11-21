package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.adkerneladn.AdkernelAdnBidder;
import org.prebid.server.bidder.adkerneladn.AdkernelAdnMetaInfo;
import org.prebid.server.bidder.adkerneladn.AdkernelAdnUsersyncer;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AdkernelAdnConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "adkernelAdn";

    @Value("${adapters.adkerneladn.enabled}")
    private boolean enabled;

    @Value("${adapters.adkerneladn.endpoint}")
    private String endpoint;

    @Value("${adapters.adkerneladn.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.adkerneladn.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${adapters.adkerneladn.deprecated-names}")
    private List<String> deprecatedNames;

    @Value("${adapters.adkerneladn.aliases}")
    private List<String> aliases;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    BidderDeps adkernelAdnBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        return bidderDeps(httpClient, httpAdapterConnector);
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
        return new AdkernelAdnMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new AdkernelAdnUsersyncer(usersyncUrl, externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new AdkernelAdnBidder(endpoint);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return null;
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
