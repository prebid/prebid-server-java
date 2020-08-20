package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.json.MissingJsonNodeToNullDeserializer;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting
 */
@Builder(toBuilder = true)
@Value
public class ExtRequestTargeting {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity
     */
    @JsonDeserialize(using = MissingJsonNodeToNullDeserializer.class)
    JsonNode pricegranularity;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity
     */
    ExtMediaTypePriceGranularity mediatypepricegranularity;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.includewinners
     */
    Boolean includewinners;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.includebrandcategory
     */
    ExtIncludeBrandCategory includebrandcategory;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.durationrangesec
     */
    List<Integer> durationrangesec;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.includebidderkeys
     */
    Boolean includebidderkeys;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.truncateattrchars
     */
    Integer truncateattrchars;
}
