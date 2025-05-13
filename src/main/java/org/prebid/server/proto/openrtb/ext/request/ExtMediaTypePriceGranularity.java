package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity
 */
@Value(staticConstructor = "of")
public class ExtMediaTypePriceGranularity {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity.banner
     */
    ObjectNode banner;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity.video
     */
    ObjectNode video;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity.native
     */
    @JsonProperty("native")
    @Getter(onMethod = @__({@JsonProperty("native")}))
    ObjectNode xNative;
}
