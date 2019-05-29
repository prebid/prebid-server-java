package org.prebid.server.proto.openrtb.ext.request.rubicon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.rubicon
 */
@Builder
@Value
public class ExtImpRubicon {

    @JsonProperty("accountId")
    Integer accountId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("zoneId")
    Integer zoneId;

    List<Integer> sizes;

    JsonNode inventory;

    JsonNode visitor;

    RubiconVideoParams video;
}
