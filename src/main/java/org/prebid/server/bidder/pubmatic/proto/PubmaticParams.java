/*
** PubmaticParams.java
*/
package org.prebid.server.bidder.pubmatic.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class PubmaticParams {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("adSlot")
    String adSlot;
}
