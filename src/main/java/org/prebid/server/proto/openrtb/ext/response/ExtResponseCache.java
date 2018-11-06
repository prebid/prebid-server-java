package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid.cache
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtResponseCache {

    CacheAsset bids;

    @JsonProperty("vastXml")
    CacheAsset vastXml;
}
