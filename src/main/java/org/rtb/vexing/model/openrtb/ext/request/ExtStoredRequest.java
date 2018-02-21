package org.rtb.vexing.model.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for ext.prebid.storedrequest
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class ExtStoredRequest {

    /**
     * Defines the contract for ext.prebid.storedrequest.id
     */
    String id;
}
