package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import java.math.BigDecimal;

public interface DeviceInfo {
    Integer getDeviceType();
    String getMake();
    String getModel();
    String getOs();
    String getOsv();
    Integer getH();
    Integer getW();
    Integer getPpi();
    BigDecimal getPixelRatio();

    String getDeviceId();
}
