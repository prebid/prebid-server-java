package org.prebid.server.proto.openrtb.ext.request.dmx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.dmx
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpDmx {

    @JsonProperty("tagid")
    String tagId;

    @JsonProperty("dmxid")
    String dmxId;

    @JsonProperty("memberid")
    String memberId;

    @JsonProperty("publisher_id")
    String publisherId;

    @JsonProperty("seller_id")
    String sellerId;
}
