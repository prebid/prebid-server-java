package org.prebid.server.cache.model;

import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

/**
 * Holds the result of bids caching.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheServiceResult {

    private static final CacheServiceResult EMPTY = CacheServiceResult.of(null, null, Collections.emptyMap());

    DebugHttpCall httpCall;

    Throwable error;

    Map<Bid, CacheIdInfo> cacheBids;

    public static CacheServiceResult empty() {
        return EMPTY;
    }
}
