package org.prebid.server.proto.openrtb.ext.request.roulax;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpRoulax {

    @JsonProperty("PublisherPath")
    String publisherPath;

    @JsonProperty("Pid")
    String pid;
}
