package org.prebid.server.proto.openrtb.ext.request.mgid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidRequest.imp[i].ext.mgid
 */
@AllArgsConstructor(
        staticName = "of"
)
@Value
public class ExtImpMgid {
    @JsonProperty(value = "accountId")
    String accountId;

    @JsonProperty(value = "placementId")
    String placementId;

    @JsonProperty("cur")
    String cur;

    @JsonProperty("currency")
    String currency;

    @JsonProperty("bidfloor")
    BigDecimal bidfloor;

    @JsonProperty("bidFloor")
    BigDecimal bidFlor;
}
