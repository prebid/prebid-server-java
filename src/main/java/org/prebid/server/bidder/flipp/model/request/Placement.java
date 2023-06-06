package org.prebid.server.bidder.flipp.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.flipp.ExtImpFlippOptions;

import java.util.List;
import java.util.Set;

@Builder(toBuilder = true)
@Value
public class Placement {

    @JsonProperty("adTypes")
    Set<Integer> adTypes;

    @JsonProperty("count")
    Integer count;

    @JsonProperty("divName")
    String divName;

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("prebid")
    PrebidRequest prebid;

    @JsonProperty("properties")
    Properties properties;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("zoneIds")
    List<Integer> zoneIds;

    @JsonProperty("options")
    ExtImpFlippOptions options;
}
