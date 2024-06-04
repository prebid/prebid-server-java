package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.engines.data.AspectPropertyValue;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;

import jakarta.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class DeviceEnricher {

    public static final String EXT_DEVICE_ID_KEY = "fiftyonedegrees_deviceId";

    private static final Map<String, Integer> DEVICE_FIELD_MAPPING = Map.ofEntries(
            Map.entry("Phone", OrtbDeviceType.PHONE.ordinal()),
            Map.entry("Console", OrtbDeviceType.SET_TOP_BOX.ordinal()),
            Map.entry("Desktop", OrtbDeviceType.PERSONAL_COMPUTER.ordinal()),
            Map.entry("EReader", OrtbDeviceType.PERSONAL_COMPUTER.ordinal()),
            Map.entry("IoT", OrtbDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("Kiosk", OrtbDeviceType.OOH_DEVICE.ordinal()),
            Map.entry("MediaHub", OrtbDeviceType.SET_TOP_BOX.ordinal()),
            Map.entry("Mobile", OrtbDeviceType.MOBILE_TABLET.ordinal()),
            Map.entry("Router", OrtbDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("SmallScreen", OrtbDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("SmartPhone", OrtbDeviceType.MOBILE_TABLET.ordinal()),
            Map.entry("SmartSpeaker", OrtbDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("SmartWatch", OrtbDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("Tablet", OrtbDeviceType.TABLET.ordinal()),
            Map.entry("Tv", OrtbDeviceType.CONNECTED_TV.ordinal()),
            Map.entry("Vehicle Display", OrtbDeviceType.PERSONAL_COMPUTER.ordinal()));

    private final Pipeline pipeline;

    public DeviceEnricher(@Nonnull Pipeline pipeline) {

        this.pipeline = Objects.requireNonNull(pipeline);
    }

    public EnrichmentResult populateDeviceInfo(Device device, CollectedEvidence collectedEvidence) {

        try (FlowData data = pipeline.createFlowData()) {
            data.addEvidence(pickRelevantFrom(collectedEvidence));
            data.process();
            final DeviceData deviceData = data.get(DeviceData.class);
            if (device == null) {
                return null;
            }
            return patchDevice(device, deviceData);
        } catch (Exception e) {
            return EnrichmentResult.builder().processingException(e).build();
        }
    }

    protected Map<String, String> pickRelevantFrom(CollectedEvidence collectedEvidence) {

        final Map<String, String> evidence = new HashMap<>();

        final String ua = collectedEvidence.deviceUA();
        if (StringUtils.isNotBlank(ua)) {
            evidence.put("header.user-agent", ua);
        }
        final Map<String, String> secureHeaders = collectedEvidence.secureHeaders();
        if (MapUtils.isNotEmpty(secureHeaders)) {
            evidence.putAll(secureHeaders);
        }
        if (!evidence.isEmpty()) {
            return evidence;
        }

        Stream.ofNullable(collectedEvidence.rawHeaders())
                .flatMap(Collection::stream)
                .forEach(rawHeader -> evidence.put("header." + rawHeader.getKey(), rawHeader.getValue()));

        return evidence;
    }

    protected EnrichmentResult patchDevice(Device device, DeviceData deviceData) {

        final List<String> updatedFields = new ArrayList<>();
        final Device.DeviceBuilder deviceBuilder = device.toBuilder();

        final Integer currentDeviceType = device.getDevicetype();
        if (!isPositive(currentDeviceType)) {
            final String rawDeviceType = getSafe(deviceData, DeviceData::getDeviceType);
            if (rawDeviceType != null) {
                final Integer properDeviceType = convertDeviceType(rawDeviceType);
                if (isPositive(properDeviceType)) {
                    deviceBuilder.devicetype(properDeviceType);
                    updatedFields.add("devicetype");
                }
            }
        }

        final String currentMake = device.getMake();
        if (StringUtils.isBlank(currentMake)) {
            final String make = getSafe(deviceData, DeviceData::getHardwareVendor);
            if (StringUtils.isNotBlank(make)) {
                deviceBuilder.make(make);
                updatedFields.add("make");
            }
        }

        if (StringUtils.isBlank(device.getModel())) {
            final String model = getSafe(deviceData, DeviceData::getHardwareModel);
            if (StringUtils.isNotBlank(model)) {
                deviceBuilder.model(model);
                updatedFields.add("model");
            } else {
                final List<String> names = getSafe(deviceData, DeviceData::getHardwareName);
                if (CollectionUtils.isNotEmpty(names)) {
                    deviceBuilder.model(String.join(",", names));
                    updatedFields.add("model");
                }
            }
        }

        if (StringUtils.isBlank(device.getOs())) {
            final String os = getSafe(deviceData, DeviceData::getPlatformName);
            if (StringUtils.isNotBlank(os)) {
                deviceBuilder.os(os);
                updatedFields.add("os");
            }
        }

        if (StringUtils.isBlank(device.getOsv())) {
            final String osv = getSafe(deviceData, DeviceData::getPlatformVersion);
            if (StringUtils.isNotBlank(osv)) {
                deviceBuilder.osv(osv);
                updatedFields.add("osv");
            }
        }

        if (!isPositive(device.getH())) {
            final Integer h = getSafe(deviceData, DeviceData::getScreenPixelsHeight);
            if (isPositive(h)) {
                deviceBuilder.h(h);
                updatedFields.add("h");
            }
        }

        if (!isPositive(device.getW())) {
            final Integer w = getSafe(deviceData, DeviceData::getScreenPixelsWidth);
            if (isPositive(w)) {
                deviceBuilder.w(w);
                updatedFields.add("w");
            }
        }

        if (!isPositive(device.getPpi())) {
            final Integer pixelsHeight = getSafe(deviceData, DeviceData::getScreenPixelsHeight);
            if (pixelsHeight != null) {
                final Double inchesHeight = getSafe(deviceData, DeviceData::getScreenInchesHeight);
                if (isPositive(inchesHeight)) {
                    deviceBuilder.ppi((int) Math.round(pixelsHeight / inchesHeight));
                    updatedFields.add("ppi");
                }
            }
        }

        final BigDecimal currentPixelRatio = device.getPxratio();
        if (!(currentPixelRatio != null && currentPixelRatio.intValue() > 0)) {
            final Double rawRatio = getSafe(deviceData, DeviceData::getPixelRatio);
            if (isPositive(rawRatio)) {
                deviceBuilder.pxratio(BigDecimal.valueOf(rawRatio));
                updatedFields.add("pxratio");
            }
        }

        final String currentDeviceId = getDeviceId(device);
        if (StringUtils.isBlank(currentDeviceId)) {
            final String deviceID = getSafe(deviceData, DeviceData::getDeviceId);
            if (StringUtils.isNotBlank(deviceID)) {
                setDeviceId(deviceBuilder, device, deviceID);
                updatedFields.add("ext." + EXT_DEVICE_ID_KEY);
            }
        }

        if (updatedFields.isEmpty()) {
            return null;
        }

        return EnrichmentResult.builder()
                .enrichedDevice(deviceBuilder.build())
                .enrichedFields(updatedFields)
                .build();
    }

    private static boolean isPositive(Integer value) {

        return value != null && value > 0;
    }

    private static boolean isPositive(Double value) {

        return value != null && value > 0;
    }

    /**
     * Consists of four components separated by a hyphen symbol:
     * Hardware-Platform-Browser-IsCrawler where
     * each Component represents an ID of the corresponding Profile.
     *
     * @see fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceId()
     */
    public static String getDeviceId(Device device) {

        final ExtDevice ext = device.getExt();
        if (ext == null) {
            return null;
        }
        final JsonNode savedValue = ext.getProperty(EXT_DEVICE_ID_KEY);
        return (savedValue != null && savedValue.isTextual()) ? savedValue.textValue() : null;
    }

    /**
     * Consists of four components separated by a hyphen symbol:
     * Hardware-Platform-Browser-IsCrawler where
     * each Component represents an ID of the corresponding Profile.
     *
     * @param deviceBuilder Writable builder to save device ID into.
     * @param device        Raw (non-builder) form of device before modification.
     * @param deviceId      New Device ID value.
     * @see fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceId()
     */
    private static void setDeviceId(Device.DeviceBuilder deviceBuilder, Device device, String deviceId) {

        ExtDevice ext = null;
        if (device != null) {
            ext = device.getExt();
        }
        if (ext == null) {
            ext = ExtDevice.empty();
        }
        ext.addProperty(EXT_DEVICE_ID_KEY, new TextNode(deviceId));
        deviceBuilder.ext(ext);
    }

    private <T> T getSafe(DeviceData deviceData, Function<DeviceData, AspectPropertyValue<T>> propertyGetter) {

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

    protected Integer convertDeviceType(String deviceType) {

        return Optional.ofNullable(DEVICE_FIELD_MAPPING.get(deviceType)).orElse(OrtbDeviceType.UNKNOWN.ordinal());
    }
}

