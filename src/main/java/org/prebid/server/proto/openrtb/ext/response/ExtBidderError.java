package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Stores error information: error code and message
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidderError {
    private int code;
    private String message;
}
