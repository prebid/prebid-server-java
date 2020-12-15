package org.prebid.server.proto.openrtb.ext.request.smartadserver;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.smartadserver
 */
@AllArgsConstructor(staticName = "of")
@Value
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
