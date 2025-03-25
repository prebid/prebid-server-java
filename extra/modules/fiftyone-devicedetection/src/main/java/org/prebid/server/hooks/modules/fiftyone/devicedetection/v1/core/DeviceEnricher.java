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
import org.prebid.server.model.UpdateResult;
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

    private static final String EXT_DEVICE_ID_KEY = "fiftyonedegrees_deviceId";

    private final Pipeline pipeline;

    public DeviceEnricher(@Nonnull Pipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline);
    }

    public static boolean shouldSkipEnriching(Device device) {
        return StringUtils.isNotEmpty(getDeviceId(device));
    }

    public EnrichmentResult populateDeviceInfo(Device device, CollectedEvidence collectedEvidence) throws Exception {
        try (FlowData data = pipeline.createFlowData()) {
            data.addEvidence(pickRelevantFrom(collectedEvidence));
            data.process();
            final DeviceData deviceData = data.get(DeviceData.class);
            if (deviceData == null) {
                return null;
            }
            final Device properDevice = Optional.ofNullable(device).orElseGet(() -> Device.builder().build());
            return patchDevice(properDevice, deviceData);
        }
    }

    private Map<String, String> pickRelevantFrom(CollectedEvidence collectedEvidence) {
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

    private EnrichmentResult patchDevice(Device device, DeviceData deviceData) {
        final List<String> updatedFields = new ArrayList<>();
        final Device.DeviceBuilder deviceBuilder = device.toBuilder();

        final UpdateResult<Integer> resolvedDeviceType = resolveDeviceType(device, deviceData);
        if (resolvedDeviceType.isUpdated()) {
            deviceBuilder.devicetype(resolvedDeviceType.getValue());
            updatedFields.add("devicetype");
        }

        final UpdateResult<String> resolvedMake = resolveMake(device, deviceData);
        if (resolvedMake.isUpdated()) {
            deviceBuilder.make(resolvedMake.getValue());
            updatedFields.add("make");
        }

        final UpdateResult<String> resolvedModel = resolveModel(device, deviceData);
        if (resolvedModel.isUpdated()) {
            deviceBuilder.model(resolvedModel.getValue());
            updatedFields.add("model");
        }

        final UpdateResult<String> resolvedOs = resolveOs(device, deviceData);
        if (resolvedOs.isUpdated()) {
            deviceBuilder.os(resolvedOs.getValue());
            updatedFields.add("os");
        }

        final UpdateResult<String> resolvedOsv = resolveOsv(device, deviceData);
        if (resolvedOsv.isUpdated()) {
            deviceBuilder.osv(resolvedOsv.getValue());
            updatedFields.add("osv");
        }

        final UpdateResult<Integer> resolvedH = resolveH(device, deviceData);
        if (resolvedH.isUpdated()) {
            deviceBuilder.h(resolvedH.getValue());
            updatedFields.add("h");
        }

        final UpdateResult<Integer> resolvedW = resolveW(device, deviceData);
        if (resolvedW.isUpdated()) {
            deviceBuilder.w(resolvedW.getValue());
            updatedFields.add("w");
        }

        final UpdateResult<Integer> resolvedPpi = resolvePpi(device, deviceData);
        if (resolvedPpi.isUpdated()) {
            deviceBuilder.ppi(resolvedPpi.getValue());
            updatedFields.add("ppi");
        }

        final UpdateResult<BigDecimal> resolvedPixelRatio = resolvePixelRatio(device, deviceData);
        if (resolvedPixelRatio.isUpdated()) {
            deviceBuilder.pxratio(resolvedPixelRatio.getValue());
            updatedFields.add("pxratio");
        }

        final UpdateResult<String> resolvedDeviceId = resolveDeviceId(device, deviceData);
        if (resolvedDeviceId.isUpdated()) {
            setDeviceId(deviceBuilder, device, resolvedDeviceId.getValue());
            updatedFields.add("ext." + EXT_DEVICE_ID_KEY);
        }

        if (updatedFields.isEmpty()) {
            return null;
        }

        return EnrichmentResult.builder()
                .enrichedDevice(deviceBuilder.build())
                .enrichedFields(updatedFields)
                .build();
    }

    private UpdateResult<Integer> resolveDeviceType(Device device, DeviceData deviceData) {
        final Integer currentDeviceType = device.getDevicetype();
        if (isPositive(currentDeviceType)) {
            return UpdateResult.unaltered(currentDeviceType);
        }

        final String rawDeviceType = getSafe(deviceData, DeviceData::getDeviceType);
        if (rawDeviceType == null) {
            return UpdateResult.unaltered(currentDeviceType);
        }

        final OrtbDeviceType properDeviceType = OrtbDeviceType.resolveFrom(rawDeviceType);
        return properDeviceType != OrtbDeviceType.UNKNOWN
                ? UpdateResult.updated(properDeviceType.ordinal())
                : UpdateResult.unaltered(currentDeviceType);
    }

    private UpdateResult<String> resolveMake(Device device, DeviceData deviceData) {
        final String currentMake = device.getMake();
        if (StringUtils.isNotBlank(currentMake)) {
            return UpdateResult.unaltered(currentMake);
        }

        final String make = getSafe(deviceData, DeviceData::getHardwareVendor);
        return StringUtils.isNotBlank(make)
                ? UpdateResult.updated(make)
                : UpdateResult.unaltered(currentMake);
    }

    private UpdateResult<String> resolveModel(Device device, DeviceData deviceData) {
        final String currentModel = device.getModel();
        if (StringUtils.isNotBlank(currentModel)) {
            return UpdateResult.unaltered(currentModel);
        }

        final String model = getSafe(deviceData, DeviceData::getHardwareModel);
        if (StringUtils.isNotBlank(model)) {
            return UpdateResult.updated(model);
        }

        final List<String> names = getSafe(deviceData, DeviceData::getHardwareName);
        return CollectionUtils.isNotEmpty(names)
                ? UpdateResult.updated(String.join(",", names))
                : UpdateResult.unaltered(currentModel);
    }

    private UpdateResult<String> resolveOs(Device device, DeviceData deviceData) {
        final String currentOs = device.getOs();
        if (StringUtils.isNotBlank(currentOs)) {
            return UpdateResult.unaltered(currentOs);
        }

        final String os = getSafe(deviceData, DeviceData::getPlatformName);
        return StringUtils.isNotBlank(os)
                ? UpdateResult.updated(os)
                : UpdateResult.unaltered(currentOs);
    }

    private UpdateResult<String> resolveOsv(Device device, DeviceData deviceData) {
        final String currentOsv = device.getOsv();
        if (StringUtils.isNotBlank(currentOsv)) {
            return UpdateResult.unaltered(currentOsv);
        }

        final String osv = getSafe(deviceData, DeviceData::getPlatformVersion);
        return StringUtils.isNotBlank(osv)
                ? UpdateResult.updated(osv)
                : UpdateResult.unaltered(currentOsv);
    }

    private UpdateResult<Integer> resolveH(Device device, DeviceData deviceData) {
        final Integer currentH = device.getH();
        if (isPositive(currentH)) {
            return UpdateResult.unaltered(currentH);
        }

        final Integer h = getSafe(deviceData, DeviceData::getScreenPixelsHeight);
        return isPositive(h)
                ? UpdateResult.updated(h)
                : UpdateResult.unaltered(currentH);
    }

    private UpdateResult<Integer> resolveW(Device device, DeviceData deviceData) {
        final Integer currentW = device.getW();
        if (isPositive(currentW)) {
            return UpdateResult.unaltered(currentW);
        }

        final Integer w = getSafe(deviceData, DeviceData::getScreenPixelsWidth);
        return isPositive(w)
                ? UpdateResult.updated(w)
                : UpdateResult.unaltered(currentW);
    }

    private UpdateResult<Integer> resolvePpi(Device device, DeviceData deviceData) {
        final Integer currentPpi = device.getPpi();
        if (isPositive(currentPpi)) {
            return UpdateResult.unaltered(currentPpi);
        }

        final Integer pixelsHeight = getSafe(deviceData, DeviceData::getScreenPixelsHeight);
        if (pixelsHeight == null) {
            return UpdateResult.unaltered(currentPpi);
        }

        final Double inchesHeight = getSafe(deviceData, DeviceData::getScreenInchesHeight);
        return isPositive(inchesHeight)
                ? UpdateResult.updated((int) Math.round(pixelsHeight / inchesHeight))
                : UpdateResult.unaltered(currentPpi);
    }

    private UpdateResult<BigDecimal> resolvePixelRatio(Device device, DeviceData deviceData) {
        final BigDecimal currentPixelRatio = device.getPxratio();
        if (currentPixelRatio != null && currentPixelRatio.intValue() > 0) {
            return UpdateResult.unaltered(currentPixelRatio);
        }

        final Double rawRatio = getSafe(deviceData, DeviceData::getPixelRatio);
        return isPositive(rawRatio)
                ? UpdateResult.updated(BigDecimal.valueOf(rawRatio))
                : UpdateResult.unaltered(currentPixelRatio);
    }

    private UpdateResult<String> resolveDeviceId(Device device, DeviceData deviceData) {
        final String currentDeviceId = getDeviceId(device);
        if (StringUtils.isNotBlank(currentDeviceId)) {
            return UpdateResult.unaltered(currentDeviceId);
        }

        final String deviceID = getSafe(deviceData, DeviceData::getDeviceId);
        return StringUtils.isNotBlank(deviceID)
                ? UpdateResult.updated(deviceID)
                : UpdateResult.unaltered(currentDeviceId);
    }

    private static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private static boolean isPositive(Double value) {
        return value != null && value > 0;
    }

    private static String getDeviceId(Device device) {
        final ExtDevice ext = device.getExt();
        if (ext == null) {
            return null;
        }
        final JsonNode savedValue = ext.getProperty(EXT_DEVICE_ID_KEY);
        return savedValue != null && savedValue.isTextual() ? savedValue.textValue() : null;
    }

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
}
