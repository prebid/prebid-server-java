package org.prebid.server.settings.bidder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class GdprInfo {

    /**
     * GDPR Vendor ID in the IAB Global Vendor List which refers to this Bidder.
     * <p>
     * The Global Vendor list can be found at https://iabeurope.eu/
     * Bidders can be registered to the list at https://register.consensu.org/
     * <p>
     * If you're not on the list, this should return 0. If cookie sync requests have GDPR consent info,
     * or the Prebid Server host company configures its deploy to be "cautious" when no GDPR info exists
     * in the request, it will _not_ sync user IDs with you.
     */
    @JsonProperty("vendorId")
    int vendorId;

    /**
     * Flag, which true value means that PBS will keep gdpr logic for bidder, otherwise bidder will keep
     * gdpr support and request should be sent without gdpr changes.
     */
    boolean enforced;
}
