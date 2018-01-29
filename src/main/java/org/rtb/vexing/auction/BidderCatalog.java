package org.rtb.vexing.auction;

import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.appnexus.AppnexusBidder;
import org.rtb.vexing.bidder.conversant.ConversantBidder;
import org.rtb.vexing.bidder.facebook.FacebookBidder;
import org.rtb.vexing.bidder.index.IndexBidder;
import org.rtb.vexing.bidder.lifestreet.LifestreetBidder;
import org.rtb.vexing.bidder.pubmatic.PubmaticBidder;
import org.rtb.vexing.bidder.pulsepoint.PulsepointBidder;
import org.rtb.vexing.bidder.rubicon.RubiconBidder;
import org.rtb.vexing.config.ApplicationConfig;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides simple access to all bidders registered so far.
 */
public class BidderCatalog {

    private final Map<String, Bidder> bidders;

    private BidderCatalog(Map<String, Bidder> bidders) {
        this.bidders = bidders;
    }

    public static BidderCatalog create(ApplicationConfig config) {
        Objects.requireNonNull(config);

        final Map<String, Bidder> bidders = Stream.of(
                rubicon(config),
                appnexus(config),
                conversant(config),
                facebook(config),
                index(config),
                lifestreet(config),
                pubmatic(config),
                pulsepoint(config))
                .collect(Collectors.toMap(Bidder::name, Function.identity()));

        return new BidderCatalog(bidders);
    }

    /**
     * Returns a bidder registered by the given name or null if there is none. Therefore this method should be called
     * only for names that previously passed validity check through calling {@link #isValidName(String)}.
     */
    public Bidder byName(String name) {
        return bidders.get(name);
    }

    /**
     * Tells if given name corresponds to any of the registered bidders.
     */
    public boolean isValidName(String name) {
        return bidders.containsKey(name);
    }

    /**
     * Returns a list of registered bidder names.
     */
    public Set<String> names() {
        return new HashSet<>(bidders.keySet());
    }

    private static Bidder rubicon(ApplicationConfig config) {
        return new RubiconBidder(
                config.getString("adapters.rubicon.endpoint"),
                config.getString("adapters.rubicon.XAPI.Username"),
                config.getString("adapters.rubicon.XAPI.Password"));
    }

    private static Bidder appnexus(ApplicationConfig config) {
        return new AppnexusBidder();
    }

    private static Bidder conversant(ApplicationConfig config) {
        return new ConversantBidder();
    }

    private static Bidder facebook(ApplicationConfig config) {
        return new FacebookBidder();
    }

    private static Bidder index(ApplicationConfig config) {
        return new IndexBidder();
    }

    private static Bidder lifestreet(ApplicationConfig config) {
        return new LifestreetBidder();
    }

    private static Bidder pubmatic(ApplicationConfig config) {
        return new PubmaticBidder();
    }

    private static Bidder pulsepoint(ApplicationConfig config) {
        return new PulsepointBidder();
    }
}
