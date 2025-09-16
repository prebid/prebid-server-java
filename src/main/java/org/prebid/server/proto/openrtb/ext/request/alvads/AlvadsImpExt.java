package org.prebid.server.proto.openrtb.ext.request.alvads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AlvadsImpExt {
    @JsonProperty("publisherUniqueId")
    String publisherUniqueId;

    @JsonProperty("endPointUrl")
    String endPointUrl;
}
