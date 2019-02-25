package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * ExtDevice defines the contract for bidrequest.device.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtDevice {

    ExtDevicePrebid prebid;
}
