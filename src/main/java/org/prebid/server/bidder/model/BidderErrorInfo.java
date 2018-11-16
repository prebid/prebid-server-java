package org.prebid.server.bidder.model;

import lombok.Builder;
import lombok.Value;

/**
 * Stores error information: error code and message
 */
@Builder
@Value
public class BidderErrorInfo {
    private int code;
    private String message;
}
