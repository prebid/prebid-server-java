package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for lineitems[].targeting.size.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class LineItemTargetingSize {

    Integer w;

    Integer h;
}
