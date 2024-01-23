package org.prebid.server.bidder.adquery.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(toBuilder = true)
@Getter
public class AdQueryDataResponse {

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("creationId")
    Integer creationId;

    String currency;

    String cpm;

    String code;

    @JsonProperty("adqLib")
    String adqLib;

    String tag;

    @JsonProperty("adDomains")
    List<String> adDomains;

    String deadlid;

    @JsonProperty("mediaType")
    AdQueryMediaType adQueryMediaType;
}
