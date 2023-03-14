package org.prebid.server.bidder.undertone.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class UndertoneRequestExt {

    @JsonProperty("id")
    Integer adapterId;

    String version;

}
