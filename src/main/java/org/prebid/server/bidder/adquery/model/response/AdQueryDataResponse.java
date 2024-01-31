package org.prebid.server.bidder.adquery.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
@Getter
public class AdQueryDataResponse {

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("creationId")
    String creationId;

    String currency;

    BigDecimal cpm;

    String code;

    @JsonProperty("adqLib")
    String adqLib;

    String tag;

    @JsonProperty("adDomains")
    List<String> adDomains;

    @JsonProperty("dealid")
    String dealId;

    @JsonProperty("mediaType")
    AdQueryMediaType adQueryMediaType;
}
