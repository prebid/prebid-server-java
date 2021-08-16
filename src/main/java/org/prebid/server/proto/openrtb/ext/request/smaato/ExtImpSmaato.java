package org.prebid.server.proto.openrtb.ext.request.smaato;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSmaato {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("adspaceId")
    String adspaceId;

    @JsonProperty("adbreakId")
    String adbreakId;
}
