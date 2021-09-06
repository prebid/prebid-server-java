package org.prebid.server.proto.openrtb.ext.request;

import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for bidrequest.device.ext.&lt;vendor&gt;
 */
@Builder
@Value
public class ExtDeviceVendor {

    public static final ExtDeviceVendor EMPTY = ExtDeviceVendor.builder().build();

    String connspeed;

    String type;

    String osfamily;

    String os;

    String osver;

    String browser;

    String browserver;

    String make;

    String model;

    String language;

    String carrier;
}
