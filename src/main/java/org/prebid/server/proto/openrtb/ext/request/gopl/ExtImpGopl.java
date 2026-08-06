package org.prebid.server.proto.openrtb.ext.request.gopl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGopl {

    @JsonProperty("siteId")
    String siteId;
    @JsonProperty("id")
    String id;
    @JsonProperty("test")
    Integer test;
}
