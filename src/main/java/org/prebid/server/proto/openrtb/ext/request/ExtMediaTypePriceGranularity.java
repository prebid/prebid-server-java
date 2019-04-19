package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtMediaTypePriceGranularity {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity.banner
     */
    JsonNode banner;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity.video
     */
    JsonNode video;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity.native
     */
    @JsonProperty("native")
    JsonNode xNative;
}
