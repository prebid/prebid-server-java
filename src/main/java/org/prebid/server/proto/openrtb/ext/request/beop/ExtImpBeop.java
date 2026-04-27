package org.prebid.server.proto.openrtb.ext.request.beop;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBeop {

    @JsonProperty("pid")
    String beopPublisherId;

    @JsonProperty("nid")
    String beopNetworkId;

    @JsonProperty("nptnid")
    @JsonAlias("ntpnid")
    String beopNetworkPartnerId;
}
