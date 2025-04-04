package org.prebid.server.proto.openrtb.ext.request.smartadserver;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.smartadserver
 */
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
}
