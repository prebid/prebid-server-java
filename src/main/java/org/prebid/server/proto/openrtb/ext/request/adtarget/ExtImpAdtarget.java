package org.prebid.server.proto.openrtb.ext.request.adtarget;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidrequest.imp[i].ext.adtarget
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdtarget {

    /**
     * Defines the contract for bidrequest.imp[i].ext.adtarget.aid
     */
    @JsonProperty("aid")
    Integer sourceId;

    /**
     * Defines the contract for bidrequest.imp[i].ext.adtarget.placementId
     */
    @JsonProperty("placementId")
    Integer placementId;

    /**
     * Defines the contract for bidrequest.imp[i].ext.adtarget.siteId
     */
    @JsonProperty("siteId")
    Integer siteId;

    /**
     * Defines the contract for bidrequest.imp[i].ext.adtarget.bidFloor
     */
    @JsonProperty("bidFloor")
    BigDecimal bidFloor;
}
