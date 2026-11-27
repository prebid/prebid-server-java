package org.prebid.server.proto.openrtb.ext.request.optout;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOptout {

    String publisher;

    @JsonProperty("adSlot")
    String adSlot;

    ObjectNode customs;

    Boolean includeOrtb2;
}
