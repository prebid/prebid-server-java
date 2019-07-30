package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.app|site.publisher.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtPublisher {

    /**
     * parentAccount would define the legal entity (publisher owner or network) that has the direct relationship
     * with the PBS host. As such, the definition depends on the PBS hosting entity.
     */
    @JsonProperty("parentAccount")
    String parentAccount;
}
