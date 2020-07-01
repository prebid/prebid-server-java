package org.prebid.server.proto.openrtb.ext.request;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

/**
 * Defines the contract for bidrequest.ext
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtRequest extends FlexibleExtension {

    /**
     * Defines the contract for bidrequest.ext.prebid
     */
    ExtRequestPrebid prebid;

    public static ExtRequest empty() {
        return of(null);
    }
}
