package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid.cache
 */
@Value
final class ExtResponseCache {

    String key;

    String url;
}
