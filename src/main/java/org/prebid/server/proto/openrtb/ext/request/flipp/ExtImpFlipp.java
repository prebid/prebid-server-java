package org.prebid.server.proto.openrtb.ext.request.flipp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtImpFlipp {

    @JsonProperty("publisherNameIdentifier")
    String publisherNameIdentifier;

    @JsonProperty("creativeType")
    String creativeType;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("zoneIds")
    List<Integer> zoneIds;

    @JsonProperty("userKey")
    String userKey;

    @JsonProperty("ip")
    String ip;

    @JsonProperty("options")
    ExtImpFlippOptions options;
}
