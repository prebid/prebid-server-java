package org.prebid.server.analytics.model;

import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Represents a transaction at /openrtb2/amp endpoint.
 */
@Builder
@Value
public class AmpEvent {

    Integer status;

    List<String> errors;

    BidResponse bidResponse;

    Map<String, String> targeting;

    String origin;
}
