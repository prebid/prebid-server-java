package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import java.math.BigDecimal;

/**
 * A subset of properties of {@link com.iab.openrtb.request.Device} this module can estimate.
 *
 * @see WritableDeviceInfo
 */
public interface DeviceInfo {
    /** @see com.iab.openrtb.request.Device#getDevicetype() */
    Integer getDeviceType();

    /** @see com.iab.openrtb.request.Device#getMake() */
    String getMake();

    /** @see com.iab.openrtb.request.Device#getModel() */
    String getModel();

    /** @see com.iab.openrtb.request.Device#getOs() */
    String getOs();

    /** @see com.iab.openrtb.request.Device#getOsv() */
    String getOsv();

    /** @see com.iab.openrtb.request.Device#getH() */
    Integer getH();

    /** @see com.iab.openrtb.request.Device#getW() */
    Integer getW();

    /** @see com.iab.openrtb.request.Device#getPpi() */
    Integer getPpi();

    /** @see com.iab.openrtb.request.Device#getPxratio() */
    BigDecimal getPixelRatio();

    /**
     * Consists of four components separated by a hyphen symbol:
     * Hardware-Platform-Browser-IsCrawler where
     * each Component represents an ID of the corresponding Profile.
     *
     * @see fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceId()
     */
    String getDeviceId();
}
