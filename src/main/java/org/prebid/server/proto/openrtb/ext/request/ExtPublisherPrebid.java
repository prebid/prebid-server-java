package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.app|site.publisher.prebid
 */
@Value(staticConstructor = "of")
public class ExtPublisherPrebid {

    /**
     * parentAccount would define the legal entity (publisher owner or network) that has the direct relationship
     * with the PBS host. As such, the definition depends on the PBS hosting entity.
     */
    @JsonProperty("parentAccount")
    String parentAccount;
}
