package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ExtIgiIgs {

    @JsonProperty("impid")
    String impId;

    ObjectNode config;

    ExtIgiIgsExt ext;
}
