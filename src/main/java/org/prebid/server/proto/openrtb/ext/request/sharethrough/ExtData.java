package org.prebid.server.proto.openrtb.ext.request.sharethrough;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtData {

    @JsonProperty("pbadslot")
    String pbAdSlot;
}
