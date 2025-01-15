package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class ExtImpPrebidBidderFields {

    ExtStoredRequest storedrequest;

    ExtOptions options;

    Integer isRewardedInventory;

    ExtImpPrebidFloors floors;

    @JsonProperty("adunitcode")
    String adUnitCode;
}
