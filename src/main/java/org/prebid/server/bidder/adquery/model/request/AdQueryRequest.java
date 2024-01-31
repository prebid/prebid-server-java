package org.prebid.server.bidder.adquery.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class AdQueryRequest {

    String v;

    @JsonProperty("placementCode")
    String placementCode;

    @JsonProperty("auctionId")
    String auctionId;

    String type;

    @JsonProperty("adUnitCode")
    String adUnitCode;

    @JsonProperty("bidQid")
    String bidQid;

    @JsonProperty("bidId")
    String bidId;

    @JsonProperty("bidIp")
    String bidIp;

    @JsonProperty("bidIpv6")
    String bidIpv6;

    @JsonProperty("bidUa")
    String bidUa;

    String bidder;

    @JsonProperty("bidPageUrl")
    String bidPageUrl;

    @JsonProperty("bidderRequestId")
    String bidderRequestId;

    @JsonProperty("bidRequestsCount")
    Integer bidRequestsCount;

    @JsonProperty("bidderRequestsCount")
    Integer bidderRequestsCount;

    String sizes;
}
