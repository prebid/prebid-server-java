package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.DeviceInfoBuilderMethodSet;

import java.math.BigDecimal;

/**
 * Internal/temporary container for estimated/inferred properties.
 *
 * @see com.iab.openrtb.request.Device
 */
@Builder(toBuilder = true)
@Value
public class DeviceInfoClone implements DeviceInfo {
    /** @see com.iab.openrtb.request.Device#getDevicetype()  */
    Integer deviceType;

    /** @see com.iab.openrtb.request.Device#getMake()  */
    String make;

    /** @see com.iab.openrtb.request.Device#getModel()  */
    String model;

    /** @see com.iab.openrtb.request.Device#getOs()  */
    String os;

    /** @see com.iab.openrtb.request.Device#getOsv()  */
    String osv;

    /** @see com.iab.openrtb.request.Device#getH()  */
    Integer h;

    /** @see com.iab.openrtb.request.Device#getW()  */
    Integer w;

    /** @see com.iab.openrtb.request.Device#getPpi()  */
    Integer ppi;

    /** @see com.iab.openrtb.request.Device#getPxratio()  */
    BigDecimal pixelRatio;

    /**
     * Consists of four components separated by a hyphen symbol:
     * Hardware-Platform-Browser-IsCrawler where
     * each Component represents an ID of the corresponding Profile.
     *
     * @see fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceId()
     */
    String deviceId;

    public static final DeviceInfoBuilderMethodSet<DeviceInfoClone, DeviceInfoClone.DeviceInfoCloneBuilder>
            BUILDER_METHOD_SET = new DeviceInfoBuilderMethodSet<>(
                    DeviceInfoClone::toBuilder,
                    DeviceInfoClone.DeviceInfoCloneBuilder::build,
                    DeviceInfoClone.DeviceInfoCloneBuilder::deviceType,
                    DeviceInfoClone.DeviceInfoCloneBuilder::make,
                    DeviceInfoClone.DeviceInfoCloneBuilder::model,
                    DeviceInfoClone.DeviceInfoCloneBuilder::os,
                    DeviceInfoClone.DeviceInfoCloneBuilder::osv,
                    DeviceInfoClone.DeviceInfoCloneBuilder::h,
                    DeviceInfoClone.DeviceInfoCloneBuilder::w,
                    DeviceInfoClone.DeviceInfoCloneBuilder::ppi,
                    DeviceInfoClone.DeviceInfoCloneBuilder::pixelRatio,
                    oldDevice -> DeviceInfoClone.DeviceInfoCloneBuilder::deviceId
            );
}
