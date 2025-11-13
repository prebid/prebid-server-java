package org.prebid.server.bidder.contxtful.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value(staticConstructor = "of")
public class ContxtfulBid {

    @JsonProperty("requestId")
    String requestId;

    BigDecimal cpm;

    String currency;

    Integer width;

    Integer height;

    @JsonProperty("creativeId")
    String creativeId;

    String adm;

    Integer ttl;

    @JsonProperty("netRevenue")
    Boolean netRevenue;

    @JsonProperty("mediaType")
    String mediaType;

    @JsonProperty("bidderCode")
    String bidderCode;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("traceId")
    String traceId;

    BigDecimal random;

    String nurl;

    String burl;

    String lurl;

    ObjectNode ext;
}
