package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class Content {

    @JsonProperty("contentid")
    String contentId;

    @JsonProperty("interactiontype")
    Integer interactionType;

    @JsonProperty("creativetype")
    Integer creativeType;

    @JsonProperty("metaData")
    MetaData metaData;

    @JsonProperty("monitor")
    List<Monitor> monitorList;

    String cur;

    Double price;
}
