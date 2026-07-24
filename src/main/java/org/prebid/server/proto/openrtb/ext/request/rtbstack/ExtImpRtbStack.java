package org.prebid.server.proto.openrtb.ext.request.rtbstack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class ExtImpRtbStack {

    @JsonProperty("route")
    String route;

    @JsonProperty("tagId")
    String tagId;

    @JsonProperty("customParams")
    Map<String, Object> customParams;
}
