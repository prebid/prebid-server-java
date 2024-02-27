package org.prebid.server.proto.openrtb.ext.request.silverpush;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpSilverPush {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;
}
