package org.prebid.server.proto.openrtb.ext.request.onetag;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOnetag {

    @JsonProperty("pubId")
    String pubId;

    ObjectNode ext;
}
