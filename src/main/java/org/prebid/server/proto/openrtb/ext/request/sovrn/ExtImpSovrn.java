package org.prebid.server.proto.openrtb.ext.request.sovrn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpSovrn {

    String tagid;

    // Used for backward compatibility with deprecated field tagId
    @JsonProperty("tagId")
    String legacyTagId;

    BigDecimal bidfloor;

    String adunitcode;
}
