package org.prebid.server.proto.openrtb.ext.request.beop;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBeop {

    @JsonProperty("pid")
    String pid;

    @JsonProperty("nid")
    String nid;

    @JsonProperty("ntpnid")
    String ntpnid;
}
