package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class Ad30 {

    @JsonProperty("adtype")
    Integer adType;

    @JsonProperty("slotid")
    String slotId;

    @JsonProperty("retcode30")
    Integer retCode;

    @JsonProperty("content")
    List<Content> contentList;
}
