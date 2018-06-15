package org.prebid.server.analytics.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Represents a transaction at /openrtb2/amp endpoint.
 */
@Builder
@Value
public class AmpEvent {

    Integer status;

    List<String> errors;

    BidResponse bidResponse;

    ObjectNode targeting;

    String origin;
}
