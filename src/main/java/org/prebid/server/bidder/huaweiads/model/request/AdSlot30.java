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

    Integer test;

    @JsonProperty("totalDuration")
    Integer totalDuration;

    Integer orientation;

    Integer w;

    Integer h;

    List<Format> format;

    @JsonProperty("detailedCreativeTypeList")
    List<String> detailedCreativeTypeList;

}
