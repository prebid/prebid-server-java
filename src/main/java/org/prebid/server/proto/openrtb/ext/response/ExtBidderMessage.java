package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Stores error information: error code and message
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidderMessage {

    int code;

    String message;
}
