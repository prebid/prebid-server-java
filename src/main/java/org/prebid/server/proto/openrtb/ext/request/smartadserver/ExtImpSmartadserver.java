package org.prebid.server.proto.openrtb.ext.request.smartadserver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSmartadserver {

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("pageId")
    Integer pageId;

    @JsonProperty("formatId")
    Integer formatId;

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty(value = "programmaticGuaranteed", access = JsonProperty.Access.WRITE_ONLY)
    boolean programmaticGuaranteed;

    @JsonProperty(value = "placementuuid", access = JsonProperty.Access.WRITE_ONLY)
    String placementUuid;

    @JsonProperty(value = "plcmtuuid", access = JsonProperty.Access.READ_ONLY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getPlcmtuuid() {
        return placementUuid;
    }
}
