package org.prebid.server.bidder;

import org.prebid.server.proto.response.BidderInfo;

/**
 * Describes the behavior for {@link MetaInfo} implementations.
 */
public interface MetaInfo {

    /**
     * Returns bidder's related meta information like maintainer email address or supported media types.
     */
    BidderInfo info();
}
