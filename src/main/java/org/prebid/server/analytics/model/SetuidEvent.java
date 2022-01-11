package org.prebid.server.analytics.model;

import lombok.Builder;
import lombok.Value;

/**
 * Represents a transaction at /setuid endpoint.
 */
@Builder
@Value
public class SetuidEvent {

    Integer status;

    String bidder;

    String uid;

    Boolean success;

    public static SetuidEvent error(int status) {
        return SetuidEvent.builder().status(status).build();
    }
}
