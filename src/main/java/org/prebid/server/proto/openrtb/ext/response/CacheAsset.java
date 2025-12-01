package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for:
 * bidresponse.seatbid.bid[i].ext.prebid.cache.bids
 * and
 * bidresponse.seatbid.bid[i].ext.prebid.cache.vastXml
 */
@Value(staticConstructor = "of")
public class CacheAsset {

    String url;

    @JsonProperty("cacheId")
    String cacheId;
}
