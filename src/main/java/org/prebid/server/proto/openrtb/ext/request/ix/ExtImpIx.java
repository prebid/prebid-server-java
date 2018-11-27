package org.prebid.server.proto.openrtb.ext.request.ix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpIx {

    @JsonProperty("siteId")
    String siteId;
}
