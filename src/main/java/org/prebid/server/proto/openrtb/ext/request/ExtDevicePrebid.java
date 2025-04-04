package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

/**
 * ExtDevice defines the contract for bidrequest.device.ext.prebid
 */
@Value(staticConstructor = "of")
public class ExtDevicePrebid {

    ExtDeviceInt interstitial;
}
