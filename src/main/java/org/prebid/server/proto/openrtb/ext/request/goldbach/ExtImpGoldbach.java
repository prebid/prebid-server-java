package org.prebid.server.proto.openrtb.ext.request.goldbach;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ExtImpGoldbach {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("slotId")
    String slotId;

    @JsonProperty("customTargeting")
    Map<String, List<String>> customTargeting;
}
