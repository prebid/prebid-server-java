package org.prebid.server.proto.openrtb.ext.request.invibes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class InvibesDebug {

    @JsonProperty("testBvid")
    String testBvid;

    @JsonProperty("testLog")
    Boolean testLog;
}
