package org.prebid.server.proto.openrtb.ext.request.invibes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class InvibesDebug {

    @JsonProperty("testBvid")
    String testBvid;

    @JsonProperty("testLog")
    Boolean testLog;
}
