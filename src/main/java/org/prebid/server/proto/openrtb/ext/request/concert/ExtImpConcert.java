package org.prebid.server.proto.openrtb.ext.request.concert;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ExtImpConcert {

    @JsonProperty("partnerId")
    String partnerId;

    @JsonProperty("placementId")
    Integer placementId;

    String site;

    String slot;

    List<List<Integer>> sizes;
}
