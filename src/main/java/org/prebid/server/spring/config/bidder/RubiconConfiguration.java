package org.prebid.server.spring.config.bidder;

import io.vertx.core.http.HttpClient;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.rubicon.RubiconAdapter;
import org.prebid.server.bidder.rubicon.RubiconBidder;
import org.prebid.server.bidder.rubicon.RubiconMetaInfo;
import org.prebid.server.bidder.rubicon.RubiconUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class RubiconConfiguration {

    private static final String BIDDER_NAME = "rubicon";

    @Value("${adapters.rubicon.endpoint}")
    private String endpoint;

    @Value("${adapters.rubicon.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.rubicon.XAPI.Username}")
    private String username;

    @Value("${adapters.rubicon.XAPI.Password}")
    private String password;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps rubiconBidderDeps(HttpClient httpClient) {
        final Usersyncer usersyncer = new RubiconUsersyncer(usersyncUrl);
        final Adapter<?, ?> adapter = new RubiconAdapter(usersyncer, endpoint, username, password);
        final Bidder<?> bidder = new RubiconBidder(endpoint, username, password);
        final BidderRequester bidderRequester = new HttpBidderRequester<>(bidder, httpClient);

        return BidderDeps.of(BIDDER_NAME, new RubiconMetaInfo(), usersyncer, adapter, bidderRequester);
    }
}
