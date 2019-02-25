package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * ExtDevice defines the contract for bidrequest.device.ext.prebid
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtDevicePrebid {

    ExtDeviceInt interstitial;
}
