package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtRequestPrebidServer {

    @JsonProperty("externalurl")
    String externalUrl;

    @JsonProperty("gvlid")
    Integer gvlId;

    String datacenter;

    String endpoint;

    public ExtRequestPrebidServer with(String endpoint) {
        return ExtRequestPrebidServer.of(
                this.externalUrl,
                this.gvlId,
                this.datacenter,
                endpoint);
    }
}
