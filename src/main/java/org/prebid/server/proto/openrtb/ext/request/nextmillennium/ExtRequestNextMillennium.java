package org.prebid.server.proto.openrtb.ext.request.nextmillennium;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtRequestNextMillennium {

    @JsonProperty("nmm_flags")
    List<String> nmmFlags;

    @JsonProperty("nm_version")
    String nmVersion;

    @JsonProperty("server_version")
    String serverVersion;
}
