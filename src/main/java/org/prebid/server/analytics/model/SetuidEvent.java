package org.prebid.server.analytics.model;

import lombok.Value;

import java.util.List;

/**
 * Represents a transaction at /setuid endpoint.
 */
@Value
public class SetuidEvent {

    Integer status;

    String bidder;

    String uid;

    List<String> errors;

    Boolean success;
}
