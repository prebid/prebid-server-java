package org.prebid.server.proto.openrtb.ext.request.colossus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpColossus {

    @JsonProperty("TagID")
    String tagId;

    @JsonProperty("groupId")
    String groupId;
}
