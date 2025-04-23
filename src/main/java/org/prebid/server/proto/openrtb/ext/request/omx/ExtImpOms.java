package org.prebid.server.proto.openrtb.ext.request.omx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOms {

    String pid;

    @JsonProperty("publisherId")
    Integer publisherId;
}
