package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.data
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidData {

    /**
     * Defines the contract for bidrequest.ext.prebid.data.bidders
     */
    List<String> bidders;
}
