package org.prebid.server.analytics.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

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
}
