package org.prebid.server.proto.openrtb.ext.request.smaato;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSmaato {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("adspaceId")
    String adspaceId;

    @JsonProperty("adbreakId")
    String adbreakId;
}
