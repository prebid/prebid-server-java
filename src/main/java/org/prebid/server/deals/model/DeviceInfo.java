package org.prebid.server.deals.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Builder
@Value
public class DeviceInfo {

    @NonNull
    String vendor;

    DeviceType deviceType;

    String deviceTypeRaw;

    String osfamily;

    String os;

    String osVersion;

    String manufacturer;

    String model;

    String browser;

    String browserVersion;

    String carrier;

    String language;
}
