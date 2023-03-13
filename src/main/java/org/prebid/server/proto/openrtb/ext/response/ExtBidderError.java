package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

import java.util.Set;

/**
 * Stores error information: error code and message
 */
@Value(staticConstructor = "of")
public class ExtBidderError {

    int code;

    String message;

    Set<String> impIds;

    public static ExtBidderError of(int code, String message) {
        return of(code, message, null);
    }
}
