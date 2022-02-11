package org.prebid.server.proto.openrtb.ext.request.adnuntius;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdnuntius {

    @JsonProperty("auId")
    String auId;

    String network;
}
