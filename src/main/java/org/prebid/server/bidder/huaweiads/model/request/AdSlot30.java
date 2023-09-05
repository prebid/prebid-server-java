package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class AdSlot30 {

    @JsonProperty("slotid")
    String slotId;

    @JsonProperty("adtype")
    Integer adType;

    @JsonProperty("test")
    Integer test;

    @JsonProperty("totalDuration")
    Integer totalDuration;

    @JsonProperty("orientation")
    Integer orientation;

    @JsonProperty("w")
    Integer w;

    @JsonProperty("h")
    Integer h;

    @JsonProperty("format")
    List<Format> format;

    @JsonProperty("detailedCreativeTypeList")
    List<String> detailedCreativeTypeList;

}
