package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for:
 * bidresponse.seatbid.bid[i].ext.prebid.cache.bids
 * and
 * bidresponse.seatbid.bid[i].ext.prebid.cache.vastXml
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheAsset {

    String url;

    String cacheId;
}
