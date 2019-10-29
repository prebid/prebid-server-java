package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestTargeting {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity
     */
    JsonNode pricegranularity;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.mediatypepricegranularity
     */
    ExtMediaTypePriceGranularity mediatypepricegranularity;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.currency
     */
    ExtCurrency currency;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.includewinners
     */
    Boolean includewinners;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.includeBrandCategory
     */
    ExtIncludeBrandCategory includebrandcategory;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.durationRangeSec
     */
    List<Integer> durationrangeSec;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.includebidderkeys
     */
    Boolean includebidderkeys;
}
