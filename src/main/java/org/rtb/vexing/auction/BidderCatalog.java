package org.rtb.vexing.auction;

import org.rtb.vexing.bidder.Bidder;

import java.util.Map;

public class BidderCatalog {

    private final Map<String, Bidder> bidders;

    private BidderCatalog(Map<String, Bidder> bidders) {
        this.bidders = bidders;
    }

    public Bidder byName(String name) {
        return bidders.get(name);
    }

    public boolean isValidName(String name) {
        return bidders.containsKey(name);
    }
}
