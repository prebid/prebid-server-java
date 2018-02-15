package org.rtb.vexing.model.openrtb.ext.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Defines the contract for bidrequest.ext.prebid
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtRequestPrebid {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting
     */
    ExtRequestTargeting targeting;

    /**
     * Defines the contract for bidrequest.ext.prebid.storedrequest
     */
    ExtStoredRequest storedrequest;

    /**
     * Defines the contract for bidrequest.ext.prebid.cache
     */
    ExtRequestPrebidCache cache;
}
