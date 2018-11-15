package org.prebid.server.bidder.pubmatic.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class PubmaticParams {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("adSlot")
    String adSlot;

    ObjectNode wrapper;

    Map<String, String> keywords;
}
