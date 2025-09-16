package org.prebid.server.proto.openrtb.ext.request;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

/**
 * Defines the contract for bidrequest.app|site.publisher.ext
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtPublisher extends FlexibleExtension {

    /**
     * Defines the contract for bidrequest.app|site.publisher.prebid
     */
    ExtPublisherPrebid prebid;

    public static ExtPublisher empty() {
        return of(null);
    }
}
