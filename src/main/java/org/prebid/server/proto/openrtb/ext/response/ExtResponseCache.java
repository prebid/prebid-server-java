package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid.cache
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class ExtResponseCache {

    CacheAsset vastXml;

    CacheAsset bids;
}
