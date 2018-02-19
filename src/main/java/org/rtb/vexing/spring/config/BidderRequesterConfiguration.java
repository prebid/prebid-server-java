package org.rtb.vexing.spring.config;

import io.vertx.core.http.HttpClient;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.auction.BidderRequesterCatalog;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.BidderRequester;
import org.rtb.vexing.bidder.HttpAdapterRequester;
import org.rtb.vexing.bidder.HttpBidderRequester;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BidderRequesterConfiguration {

    @Bean
    BidderRequesterCatalog bidderRequesterCatalog(List<BidderRequester> bidderRequesters) {
        return new BidderRequesterCatalog(bidderRequesters);
    }

    @Bean
    BidderRequester rubiconHttpConnector(Bidder rubiconBidder, HttpClient httpClient) {
        return new HttpBidderRequester(rubiconBidder, httpClient);
    }

    @Bean
    BidderRequester appnexusHttpConnector(Bidder appnexusBidder, HttpClient httpClient) {
        return new HttpBidderRequester(appnexusBidder, httpClient);
    }

    @Bean
    BidderRequester pulsepointHttpConnector(Adapter pulsepointAdapter,
                                            org.rtb.vexing.adapter.HttpConnector httpConnector) {
        return new HttpAdapterRequester(pulsepointAdapter, httpConnector);
    }

    @Bean
    BidderRequester facebookHttpConnector(Adapter facebookAdapter, org.rtb.vexing.adapter.HttpConnector httpConnector) {
        return new HttpAdapterRequester(facebookAdapter, httpConnector);
    }

    @Bean
    BidderRequester indexHttpConnector(Adapter indexAdapter, org.rtb.vexing.adapter.HttpConnector httpConnector) {
        return new HttpAdapterRequester(indexAdapter, httpConnector);
    }

    @Bean
    BidderRequester lifestreetHttpConnector(Adapter lifestreetAdapter,
                                            org.rtb.vexing.adapter.HttpConnector httpConnector) {
        return new HttpAdapterRequester(lifestreetAdapter, httpConnector);
    }

    @Bean
    BidderRequester pubmaticHttpConnector(Adapter pubmaticAdapter, org.rtb.vexing.adapter.HttpConnector httpConnector) {
        return new HttpAdapterRequester(pubmaticAdapter, httpConnector);
    }

    @Bean
    BidderRequester conversantHttpConnector(Adapter conversantAdapter,
                                            org.rtb.vexing.adapter.HttpConnector httpConnector) {
        return new HttpAdapterRequester(conversantAdapter, httpConnector);
    }
}
