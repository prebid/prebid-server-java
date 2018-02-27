package org.prebid.server.spring.config;

import io.vertx.core.http.HttpClient;
import org.prebid.server.auction.BidderRequesterCatalog;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.HttpConnector;
import org.prebid.server.bidder.appnexus.AppnexusBidder;
import org.prebid.server.bidder.conversant.ConversantAdapter;
import org.prebid.server.bidder.facebook.FacebookAdapter;
import org.prebid.server.bidder.index.IndexAdapter;
import org.prebid.server.bidder.lifestreet.LifestreetAdapter;
import org.prebid.server.bidder.pubmatic.PubmaticAdapter;
import org.prebid.server.bidder.pulsepoint.PulsepointAdapter;
import org.prebid.server.bidder.rubicon.RubiconBidder;
import org.prebid.server.usersyncer.ConversantUsersyncer;
import org.prebid.server.usersyncer.FacebookUsersyncer;
import org.prebid.server.usersyncer.IndexUsersyncer;
import org.prebid.server.usersyncer.LifestreetUsersyncer;
import org.prebid.server.usersyncer.PubmaticUsersyncer;
import org.prebid.server.usersyncer.PulsepointUsersyncer;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

@Configuration
public class BidderRequesterConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderRequesterCatalog bidderRequesterCatalog(List<BidderRequester> bidderRequesters) {
        return new BidderRequesterCatalog(bidderRequesters);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderRequester appnexusHttpConnector(AppnexusBidder appnexusBidder, HttpClient httpClient) {
        return new HttpBidderRequester(appnexusBidder, httpClient);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderRequester conversantHttpConnector(ConversantAdapter conversantAdapter,
                                            ConversantUsersyncer conversantUsersyncer, HttpConnector httpConnector) {
        return new HttpAdapterRequester(conversantAdapter, conversantUsersyncer, httpConnector);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(name = {"adapters.facebook.usersync-url", "adapters.facebook.platformId"})
    BidderRequester facebookHttpConnector(FacebookAdapter facebookAdapter, FacebookUsersyncer facebookUsersyncer,
                                          HttpConnector httpConnector) {
        return new HttpAdapterRequester(facebookAdapter, facebookUsersyncer, httpConnector);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(name = "adapters.indexexchange.endpoint")
    BidderRequester indexHttpConnector(IndexAdapter indexAdapter, IndexUsersyncer indexUsersyncer,
                                       HttpConnector httpConnector) {
        return new HttpAdapterRequester(indexAdapter, indexUsersyncer, httpConnector);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderRequester lifestreetHttpConnector(LifestreetAdapter lifestreetAdapter,
                                            LifestreetUsersyncer lifestreetUsersyncer, HttpConnector httpConnector) {
        return new HttpAdapterRequester(lifestreetAdapter, lifestreetUsersyncer, httpConnector);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderRequester pubmaticHttpConnector(PubmaticAdapter pubmaticAdapter, PubmaticUsersyncer pubmaticUsersyncer,
                                          HttpConnector httpConnector) {
        return new HttpAdapterRequester(pubmaticAdapter, pubmaticUsersyncer, httpConnector);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderRequester pulsepointHttpConnector(PulsepointAdapter pulsepointAdapter,
                                            PulsepointUsersyncer pulsepointUsersyncer, HttpConnector httpConnector) {
        return new HttpAdapterRequester(pulsepointAdapter, pulsepointUsersyncer, httpConnector);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderRequester rubiconHttpConnector(RubiconBidder rubiconBidder, HttpClient httpClient) {
        return new HttpBidderRequester(rubiconBidder, httpClient);
    }
}
