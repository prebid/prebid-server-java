package org.prebid.server.bidder.smaato.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class SmaatoNativeAd {

    @JsonProperty("native")
    ObjectNode nativeRequest;

}
