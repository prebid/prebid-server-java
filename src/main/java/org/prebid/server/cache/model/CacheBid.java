package org.prebid.server.cache.model;

import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds the information about cache TTL for particular {@link Bid} to be send to Cache Service.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheBid {

    Bid bid;

    Integer ttl;
}
