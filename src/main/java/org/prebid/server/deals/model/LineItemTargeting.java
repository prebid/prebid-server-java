package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for lineitems[].targeting.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class LineItemTargeting {

    List<LineItemTargetingSize> size;
}
