package org.prebid.server.proto.openrtb.ext.request.dmx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidRequest.imp[i].ext.dmx
 */
@Builder
@Value
public class ExtImpDmx {

    @JsonProperty("tagid")
    String tagId;

    @JsonProperty("dmxid")
    String dmxId;

    @JsonProperty("memberid")
    String memberId;

    String publisherId;

    String sellerId;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;
}
