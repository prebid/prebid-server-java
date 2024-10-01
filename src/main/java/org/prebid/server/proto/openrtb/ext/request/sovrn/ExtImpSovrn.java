package org.prebid.server.proto.openrtb.ext.request.sovrn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpSovrn {

    String tagid;

    // Used for backward compatibility with deprecated field tagId
    @JsonProperty("tagId")
    String legacyTagId;

    JsonNode bidfloor;

    String adunitcode;
}
