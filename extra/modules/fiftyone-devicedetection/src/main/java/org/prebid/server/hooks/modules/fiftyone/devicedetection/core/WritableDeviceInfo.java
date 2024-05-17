package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import java.math.BigDecimal;

/**
 * A writable counterpart to {@link DeviceInfo}
 */
public interface WritableDeviceInfo {
    /** @see com.iab.openrtb.request.Device.DeviceBuilder#devicetype(Integer) */
    void setDeviceType(Integer deviceType);

    /** @see com.iab.openrtb.request.Device.DeviceBuilder#make(String) */
    void setMake(String make);

    /** @see com.iab.openrtb.request.Device.DeviceBuilder#model(String) */
    void setModel(String model);

    /** @see com.iab.openrtb.request.Device.DeviceBuilder#os(String) */
    void setOs(String os);

    /** @see com.iab.openrtb.request.Device.DeviceBuilder#osv(String) */
    void setOsv(String osv);

    /** @see com.iab.openrtb.request.Device.DeviceBuilder#h(Integer) */
    void setH(Integer h);

    /** @see com.iab.openrtb.request.Device.DeviceBuilder#w(Integer) */
    void setW(Integer w);

    /** @see com.iab.openrtb.request.Device.DeviceBuilder#ppi(Integer) */
    void setPpi(Integer ppi);

    /** @see com.iab.openrtb.request.Device.DeviceBuilder#pxratio(BigDecimal) */
    void setPixelRatio(BigDecimal pixelRatio);

    /**
     * Consists of four components separated by a hyphen symbol:
     * Hardware-Platform-Browser-IsCrawler where
     * each Component represents an ID of the corresponding Profile.
     *
     * @see fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceId()
     */
    void setDeviceId(String deviceId);
}
