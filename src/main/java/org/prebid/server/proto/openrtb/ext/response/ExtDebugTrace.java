package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext.debug.trace
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtDebugTrace {

    /**
     * Defines the contract for bidresponse.ext.debug.trace.deals
     */
    List<ExtTraceDeal> deals;

    /**
     * Defines the contract for bidresponse.ext.debug.trace.lineItems
     */

    @JsonProperty("lineitems")
    Map<String, List<ExtTraceDeal>> lineItems;
}
