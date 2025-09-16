package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

/**
 * Defines the contract for ext.prebid.storedrequest
 */
@Value(staticConstructor = "of")
public class ExtStoredRequest {

    /**
     * Defines the contract for ext.prebid.storedrequest.id
     */
    String id;
}
