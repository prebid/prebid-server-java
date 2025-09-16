package org.prebid.server.proto.openrtb.ext.request.adkerneladn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adkernelAdn
 */
@Builder
@Value
public class ExtImpAdkernelAdn {

    @JsonProperty("pubId")
    Integer pubId;
}
