package org.rtb.vexing.model.openrtb.ext.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Defines the contract for bidrequest.ext.prebid.cache
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtRequestPrebidCache {

    /**
     * Defines the contract for bidrequest.ext.prebid.cache.bids
     */
    JsonNode bids;
}
