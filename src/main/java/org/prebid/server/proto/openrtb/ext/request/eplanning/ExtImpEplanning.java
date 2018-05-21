package org.prebid.server.proto.openrtb.ext.request.eplanning;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.eplanning
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpEplanning {

    /**
     * Defines the contract for bidrequest.imp[i].ext.eplanning.exchange_id
     */
    String exchangeId;
}
