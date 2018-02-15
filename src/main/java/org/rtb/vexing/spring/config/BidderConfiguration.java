package org.rtb.vexing.spring.config;

import org.rtb.vexing.auction.BidderCatalog;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.appnexus.AppnexusBidder;
import org.rtb.vexing.bidder.conversant.ConversantBidder;
import org.rtb.vexing.bidder.facebook.FacebookBidder;
import org.rtb.vexing.bidder.index.IndexBidder;
import org.rtb.vexing.bidder.lifestreet.LifestreetBidder;
import org.rtb.vexing.bidder.pubmatic.PubmaticBidder;
import org.rtb.vexing.bidder.pulsepoint.PulsepointBidder;
import org.rtb.vexing.bidder.rubicon.RubiconBidder;
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
    IndexBidder indexBidderBidder() {
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
