package org.prebid.server.deals.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for lineItems[].sizes[].
 */
@AllArgsConstructor(staticName = "of")
@Value
public class LineItemSize {

    Integer w;

    Integer h;
}
