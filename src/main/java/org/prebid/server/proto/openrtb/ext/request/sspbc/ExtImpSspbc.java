package org.prebid.server.proto.openrtb.ext.request.sspbc;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSspbc {

    @JsonProperty("siteId")
    String siteId;
    @JsonProperty("id")
    String id;
    @JsonProperty("test")
    Integer test;
}
