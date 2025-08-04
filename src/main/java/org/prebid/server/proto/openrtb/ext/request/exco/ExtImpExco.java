package org.prebid.server.proto.openrtb.ext.request.exco;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpExco {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("tagId")
    String tagId;
}
