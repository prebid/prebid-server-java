package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.engines.data.AspectPropertyValue;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceTypeConverter;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public final class DeviceDataWrapper implements DeviceInfo {
    private final DeviceData deviceData;
    private final DeviceTypeConverter deviceTypeConverter;

    public DeviceDataWrapper(DeviceData deviceData, DeviceTypeConverter deviceTypeConverter) {
        this.deviceData = deviceData;
        this.deviceTypeConverter = deviceTypeConverter;
    }

    @Override
    public Integer getDeviceType() {
        final String rawDeviceType = getSafe(DeviceData::getDeviceType);
        if (rawDeviceType == null) {
            return null;
        }
        return deviceTypeConverter.apply(rawDeviceType);
    }

    @Override
    public String getMake() {
        return getSafe(DeviceData::getHardwareVendor);
    }

    @Override
    public String getModel() {
        final String model = getSafe(DeviceData::getHardwareModel);
        if (!(model == null || model.isEmpty() || model.equals("Unknown"))) {
            return model;
        }
        final List<String> names = getSafe(DeviceData::getHardwareName);
        if (names == null || names.isEmpty()) {
            return null;
        }
        return String.join(",", names);
    }

    @Override
    public String getOs() {
        return getSafe(DeviceData::getPlatformName);
    }

    @Override
    public String getOsv() {
        return getSafe(DeviceData::getPlatformVersion);
    }

    @Override
    public Integer getH() {
        return getSafe(DeviceData::getScreenPixelsHeight);
    }

    @Override
    public Integer getW() {
        return getSafe(DeviceData::getScreenPixelsWidth);
    }

    @Override
    public Integer getPpi() {
        final Integer pixelsHeight = getSafe(DeviceData::getScreenPixelsHeight);
        if (pixelsHeight == null) {
            return null;
        }
        final Double inchesHeight = getSafe(DeviceData::getScreenInchesHeight);
        if (inchesHeight == null || inchesHeight == 0) {
            return null;
        }
        return (int)Math.round(pixelsHeight / inchesHeight);
    }

    @Override
    public BigDecimal getPixelRatio() {
        final Double rawRatio = getSafe(DeviceData::getPixelRatio);
        return (rawRatio != null) ? BigDecimal.valueOf(rawRatio) : null;
    }

    @Override
    public String getDeviceId() {
        return getSafe(DeviceData::getDeviceId);
    }

    private <T> T getSafe(Function<DeviceData, AspectPropertyValue<T>> propertyGetter) {
        try {
            final AspectPropertyValue<T> propertyValue = propertyGetter.apply(deviceData);
            if (propertyValue != null && propertyValue.hasValue()) {
                return propertyValue.getValue();
            }
        } catch (Exception e) {
            // nop -- not interested in errors on getting missing values.
        }
        return null;
    }
}
