package org.prebid.server.proto.openrtb.ext.request;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

/**
 * ExtDevice defines the contract for bidrequest.device.ext
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtDevice extends FlexibleExtension {

    Integer atts;

    ExtDevicePrebid prebid;

    public static ExtDevice empty() {
        return of(null, null);
    }
}
