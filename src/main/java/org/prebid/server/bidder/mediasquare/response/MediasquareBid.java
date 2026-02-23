package org.prebid.server.bidder.mediasquare.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class MediasquareBid {

    String id;

    String ad;

    String bidId;

    String bidder;

    BigDecimal cpm;

    String currency;

    String creativeId;

    Integer height;

    Integer width;

    Boolean netRevenue;

    String transactionId;

    Integer ttl;

    ObjectNode video;

    @JsonProperty("native")
    ObjectNode nativeResponse;

    List<String> adomain;

    ObjectNode dsa;

    String burl;
}
