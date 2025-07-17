package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.iab.openrtb.request.Device;
import com.scientiamobile.wurfl.core.exc.CapabilityNotDefinedException;
import com.scientiamobile.wurfl.core.exc.VirtualCapabilityNotDefinedException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;

import java.math.BigDecimal;
import java.util.Set;

public class OrtbDeviceUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(OrtbDeviceUpdater.class);
    private static final String WURFL_PROPERTY = "wurfl";
    private static final Set<String> VIRTUAL_CAPABILITY_NAMES = Set.of(
            "advertised_device_os",
            "advertised_device_os_version",
            "pixel_density");

    public Device update(Device ortbDevice, com.scientiamobile.wurfl.core.Device wurflDevice,
                         Set<String> staticCaps, Set<String> virtualCaps, boolean addExtCaps) {
        final Device.DeviceBuilder deviceBuilder = ortbDevice.toBuilder();

        // make
        final UpdateResult<String> updatedMake = tryUpdateStringField(ortbDevice.getMake(), wurflDevice,
                "brand_name");
        if (updatedMake.isUpdated()) {
            deviceBuilder.make(updatedMake.getValue());
        }

        // model
        final UpdateResult<String> updatedModel = tryUpdateStringField(ortbDevice.getModel(), wurflDevice,
                "model_name");
        if (updatedModel.isUpdated()) {
            deviceBuilder.model(updatedModel.getValue());
        }

        // deviceType
        final UpdateResult<Integer> updatedDeviceType = tryUpdateDeviceTypeField(ortbDevice.getDevicetype(),
                getOrtb2DeviceType(wurflDevice));
        if (updatedDeviceType.isUpdated()) {
            deviceBuilder.devicetype(updatedDeviceType.getValue());
        }

        // os
        final UpdateResult<String> updatedOS = tryUpdateStringField(ortbDevice.getOs(), wurflDevice,
                "advertised_device_os");
        if (updatedOS.isUpdated()) {
            deviceBuilder.os(updatedOS.getValue());
        }

        // os version
        final UpdateResult<String> updatedOsv = tryUpdateStringField(ortbDevice.getOsv(), wurflDevice,
                "advertised_device_os_version");
        if (updatedOS.isUpdated()) {
            deviceBuilder.osv(updatedOsv.getValue());
        }

        // h (resolution height)
        final UpdateResult<Integer> updatedH = tryUpdateIntegerField(ortbDevice.getH(), wurflDevice,
                "resolution_height", false);
        if (updatedH.isUpdated()) {
            deviceBuilder.h(updatedH.getValue());
        }

        // w (resolution height)
        final UpdateResult<Integer> updatedW = tryUpdateIntegerField(ortbDevice.getW(), wurflDevice,
                "resolution_width", false);
        if (updatedW.isUpdated()) {
            deviceBuilder.w(updatedW.getValue());
        }

        // Pixels per inch
        final UpdateResult<Integer> updatedPpi = tryUpdateIntegerField(ortbDevice.getPpi(), wurflDevice,
                "pixel_density", false);
        if (updatedPpi.isUpdated()) {
            deviceBuilder.ppi(updatedPpi.getValue());
        }

        // Pixel ratio
        final UpdateResult<BigDecimal> updatedPxRatio = tryUpdateBigDecimalField(ortbDevice.getPxratio(), wurflDevice,
                "density_class");
        if (updatedPxRatio.isUpdated()) {
            deviceBuilder.pxratio(updatedPxRatio.getValue());
        }

        // Javascript support
        final UpdateResult<Integer> updatedJs = tryUpdateIntegerField(ortbDevice.getJs(), wurflDevice,
                "ajax_support_javascript", true);
        if (updatedJs.isUpdated()) {
            deviceBuilder.js(updatedJs.getValue());
        }

        // Ext
        final ExtWURFLMapper extMapper = ExtWURFLMapper.builder()
                .wurflDevice(wurflDevice)
                .staticCaps(staticCaps)
                .virtualCaps(virtualCaps)
                .addExtCaps(addExtCaps)
                .build();
        final ExtDevice updatedExt = ExtDevice.empty();
        final ExtDevice ortbDeviceExt = ortbDevice.getExt();

        if (ortbDeviceExt != null) {
            updatedExt.addProperties(ortbDeviceExt.getProperties());
            if (!ortbDeviceExt.containsProperty(WURFL_PROPERTY)) {
                updatedExt.addProperty("wurfl", extMapper.mapExtProperties());
            }
        } else {
            updatedExt.addProperty("wurfl", extMapper.mapExtProperties());
        }
        deviceBuilder.ext(updatedExt);
        return deviceBuilder.build();
    }

    private UpdateResult<String> tryUpdateStringField(String fromOrtbDevice,
                                                      com.scientiamobile.wurfl.core.Device wurflDevice,
                                                      String capName) {
        if (StringUtils.isNotBlank(fromOrtbDevice)) {
            return UpdateResult.unaltered(fromOrtbDevice);
        }

        final String fromWurfl = isVirtualCapability(capName)
                ? wurflDevice.getVirtualCapability(capName)
                : wurflDevice.getCapability(capName);

        if (fromWurfl != null) {
            return UpdateResult.updated(fromWurfl);
        }

        return UpdateResult.unaltered(fromOrtbDevice);
    }

    private UpdateResult<Integer> tryUpdateIntegerField(Integer fromOrtbDevice,
                                                        com.scientiamobile.wurfl.core.Device wurflDevice,
                                                        String capName, boolean convertFromBool) {
        if (fromOrtbDevice != null) {
            return UpdateResult.unaltered(fromOrtbDevice);
        }

        final String fromWurfl = isVirtualCapability(capName)
                ? wurflDevice.getVirtualCapability(capName)
                : wurflDevice.getCapability(capName);

        if (StringUtils.isNotBlank(fromWurfl)) {

            if (convertFromBool) {
                return fromWurfl.equalsIgnoreCase("true")
                        ? UpdateResult.updated(1)
                        : UpdateResult.updated(0);
            }

            return UpdateResult.updated(Integer.parseInt(fromWurfl));
        }
        return UpdateResult.unaltered(fromOrtbDevice);
    }

    private UpdateResult<BigDecimal> tryUpdateBigDecimalField(BigDecimal fromOrtbDevice,
                                                              com.scientiamobile.wurfl.core.Device wurflDevice,
                                                              String capName) {
        if (fromOrtbDevice != null) {
            return UpdateResult.unaltered(fromOrtbDevice);
        }

        final String fromWurfl = isVirtualCapability(capName)
                ? wurflDevice.getVirtualCapability(capName)
                : wurflDevice.getCapability(capName);

        if (fromWurfl != null) {
            final BigDecimal pxRatio = new BigDecimal(fromWurfl);
            return UpdateResult.updated(pxRatio);
        }
        return UpdateResult.unaltered(fromOrtbDevice);
    }

    private boolean isVirtualCapability(String vcapName) {
        return VIRTUAL_CAPABILITY_NAMES.contains(vcapName);
    }

    private UpdateResult<Integer> tryUpdateDeviceTypeField(Integer fromOrtbDevice, Integer fromWurfl) {
        final boolean isNotNullAndPositive = fromOrtbDevice != null && fromOrtbDevice > 0;
        if (isNotNullAndPositive) {
            return UpdateResult.unaltered(fromOrtbDevice);
        }

        if (fromWurfl != null) {
            return UpdateResult.updated(fromWurfl);
        }

        return UpdateResult.unaltered(fromOrtbDevice);
    }

    public static Integer getOrtb2DeviceType(final com.scientiamobile.wurfl.core.Device wurflDevice) {
        final boolean isPhone;
        final boolean isTablet;

        if (wurflDevice.getVirtualCapabilityAsBool("is_mobile")) {
            // if at least one if these capabilities is not defined the mobile device type is undefined
            try {
                isPhone = wurflDevice.getVirtualCapabilityAsBool("is_phone");
                isTablet = wurflDevice.getCapabilityAsBool("is_tablet");
            } catch (CapabilityNotDefinedException | VirtualCapabilityNotDefinedException e) {
                return null;
            }

            if (isPhone || isTablet) {
                return 1;
            }
            return 6;
        }

        // desktop device
        if (wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")) {
            return 2;
        }

        // connected tv
        if (wurflDevice.getCapabilityAsBool("is_connected_tv")) {
            return 3;
        }

        if (wurflDevice.getCapabilityAsBool("is_phone")) {
            return 4;
        }

        if (wurflDevice.getCapabilityAsBool("is_tablet")) {
            return 5;
        }

        if (wurflDevice.getCapabilityAsBool("is_ott")) {
            return 7;
        }

        return null; // Return null for an undefined device type
    }

}
