package org.prebid.spring.config;

import org.prebid.auction.BidderCatalog;
import org.prebid.bidder.Bidder;
import org.prebid.bidder.appnexus.AppnexusBidder;
import org.prebid.bidder.conversant.ConversantBidder;
import org.prebid.bidder.facebook.FacebookBidder;
import org.prebid.bidder.index.IndexBidder;
import org.prebid.bidder.lifestreet.LifestreetBidder;
import org.prebid.bidder.pubmatic.PubmaticBidder;
import org.prebid.bidder.pulsepoint.PulsepointBidder;
import org.prebid.bidder.rubicon.RubiconBidder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BidderConfiguration {

    @Bean
    BidderCatalog bidderCatalog(List<Bidder> bidders) {
        return new BidderCatalog(bidders);
    }

    @Bean
    RubiconBidder rubiconBidder(
            @Value("${adapters.rubicon.endpoint}") String endpoint,
            @Value("${adapters.rubicon.XAPI.Username}") String username,
            @Value("${adapters.rubicon.XAPI.Password}") String password) {

        return new RubiconBidder(endpoint, username, password);
    }

    @Bean
    AppnexusBidder appnexusBidder(
            @Value("${adapters.appnexus.endpoint}") String endpoint) {
        return new AppnexusBidder(endpoint);
    }

    @Bean
    PulsepointBidder pulsepointBidder() {
        return new PulsepointBidder();
    }

    @Bean
    FacebookBidder facebookBidder() {
        return new FacebookBidder();
    }

    @Bean
    IndexBidder indexBidder() {
        return new IndexBidder();
    }

    @Bean
    LifestreetBidder lifestreetBidder() {
        return new LifestreetBidder();
    }

    @Bean
    PubmaticBidder pubmaticBidder() {
        return new PubmaticBidder();
    }

    @Bean
    ConversantBidder conversantBidder() {
        return new ConversantBidder();
    }
}
