package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.app|site.publisher.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtPublisher {

    /**
     * Defines the contract for bidrequest.app|site.publisher.prebid
     */
    ExtPublisherPrebid prebid;
}
