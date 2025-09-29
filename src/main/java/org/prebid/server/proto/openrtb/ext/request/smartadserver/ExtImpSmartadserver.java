package org.prebid.server.proto.openrtb.ext.request.smartadserver;

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
}
