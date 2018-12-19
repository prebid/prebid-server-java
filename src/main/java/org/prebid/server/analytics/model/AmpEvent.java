package org.prebid.server.analytics.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;

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

    RoutingContext context;

    UidsCookie uidsCookie;

    BidRequest bidRequest;

    BidResponse bidResponse;

    Map<String, JsonNode> targeting;

    String origin;
}
