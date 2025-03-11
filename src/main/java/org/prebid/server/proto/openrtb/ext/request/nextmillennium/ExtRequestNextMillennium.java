package org.prebid.server.proto.openrtb.ext.request.nextmillennium;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtRequestNextMillennium {

    @JsonProperty("nmmFlags")
    List<String> nmmFlags;

    @JsonProperty("nmVersion")
    String nmVersion;

    @JsonProperty("serverVersion")
    String serverVersion;
}
