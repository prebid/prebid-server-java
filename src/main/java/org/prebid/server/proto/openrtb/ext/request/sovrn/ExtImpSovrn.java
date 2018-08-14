package org.prebid.server.proto.openrtb.ext.request.sovrn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpSovrn {

    String tagid;

    // Used for backward compatibility with deprecated field tagId
    @JsonProperty("tagId")
    String legacyTagId;

    Float bidfloor;
}
