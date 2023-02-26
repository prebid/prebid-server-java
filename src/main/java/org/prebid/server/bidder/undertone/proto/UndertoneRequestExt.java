package org.prebid.server.bidder.undertone.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class UndertoneRequestExt {

    @JsonProperty("id")
    int adapterId;

    @JsonProperty("version")
    String version;

}
