package org.prebid.server.analytics.reporter.greenbids.model;

import java.util.HashMap;

public class AuctionCacheManager {
    private HashMap<String, CachedAuction> cachedAuctions;
    
    public AuctionCacheManager() {
        this.cachedAuctions = new HashMap<>();
    }
    
    public CachedAuction getCachedAuction(String auctionId) {
        // Check if the auction is already in the cache
        if (!cachedAuctions.containsKey(auctionId)) {
            // If not, initialize and put a new CachedAuction object into the cache
            cachedAuctions.put(auctionId, new CachedAuction());
        }
        // Return the cached or newly created CachedAuction object
        return cachedAuctions.get(auctionId);
    }
}
