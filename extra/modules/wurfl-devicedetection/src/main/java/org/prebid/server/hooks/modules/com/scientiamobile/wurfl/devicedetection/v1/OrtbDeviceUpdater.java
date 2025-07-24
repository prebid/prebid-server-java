package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import com.iab.openrtb.request.Device;
import com.scientiamobile.wurfl.core.exc.CapabilityNotDefinedException;
import com.scientiamobile.wurfl.core.exc.VirtualCapabilityNotDefinedException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.PayloadUpdate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class OrtbDeviceUpdater implements PayloadUpdate<AuctionRequestPayload> {

    private static final Logger logger = LoggerFactory.getLogger(OrtbDeviceUpdater.class);

    private static final String WURFL_PROPERTY = "wurfl";

    private final com.scientiamobile.wurfl.core.Device wurflDevice;
    private final Set<String> staticCaps;
    private final Set<String> virtualCaps;
    private final boolean addExtCaps;
    private final JacksonMapper mapper;

    public OrtbDeviceUpdater(com.scientiamobile.wurfl.core.Device wurflDevice,
                             Set<String> staticCaps,
                             Set<String> virtualCaps,
                             boolean addExtCaps,
                             JacksonMapper mapper) {

        this.wurflDevice = Objects.requireNonNull(wurflDevice);
        this.staticCaps = Objects.requireNonNull(staticCaps);
        this.virtualCaps = Objects.requireNonNull(virtualCaps);
        this.addExtCaps = addExtCaps;
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public AuctionRequestPayload apply(AuctionRequestPayload auctionRequestPayload) {
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        return AuctionRequestPayloadImpl.of(bidRequest.toBuilder()
                .device(update(bidRequest.getDevice()))
                .build());
    }

    private Device update(Device ortbDevice) {
        final String make = tryUpdateField(ortbDevice.getMake(), this::getWurflMake);
        final String model = tryUpdateField(ortbDevice.getModel(), this::getWurflModel);
        final Integer deviceType = tryUpdateField(
                Optional.ofNullable(ortbDevice.getDevicetype())
                        .filter(it -> it > 0)
                        .orElse(null),
                this::getWurflDeviceType);
        final String os = tryUpdateField(ortbDevice.getOs(), this::getWurflOs);
        final String osv = tryUpdateField(ortbDevice.getOsv(), this::getWurflOsv);
        final Integer h = tryUpdateField(ortbDevice.getH(), this::getWurflH);
        final Integer w = tryUpdateField(ortbDevice.getW(), this::getWurflW);
        final Integer ppi = tryUpdateField(ortbDevice.getPpi(), this::getWurflPpi);
        final BigDecimal pxratio = tryUpdateField(ortbDevice.getPxratio(), this::getWurflPxRatio);
        final Integer js = tryUpdateField(ortbDevice.getJs(), this::getWurflJs);

        return ortbDevice.toBuilder()
                .make(make)
                .model(model)
                .devicetype(deviceType)
                .os(os)
                .osv(osv)
                .h(h)
                .w(w)
                .ppi(ppi)
                .pxratio(pxratio)
                .js(js)
                .ext(updateExt(ortbDevice.getExt()))
                .build();
    }

    private static <T> T tryUpdateField(T fromOrtbDevice, Supplier<T> fromWurflDeviceSupplier) {
        if (fromOrtbDevice != null) {
            return fromOrtbDevice;
        }

        final T fromWurflDevice = fromWurflDeviceSupplier.get();
        return fromWurflDevice != null
                ? fromWurflDevice
                : fromOrtbDevice;
    }

    private String getWurflMake() {
        return wurflDevice.getCapability("brand_name");
    }

    private String getWurflModel() {
        return wurflDevice.getCapability("model_name");
    }

    private Integer getWurflDeviceType() {
        try {
            if (wurflDevice.getVirtualCapabilityAsBool("is_mobile")) {
                // if at least one of these capabilities is not defined, the mobile device type is undefined
                final boolean isPhone = wurflDevice.getVirtualCapabilityAsBool("is_phone");
                final boolean isTablet = wurflDevice.getCapabilityAsBool("is_tablet");
                return isPhone || isTablet ? 1 : 6;
            }

            if (wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")) {
                return 2;
            }

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

            final String physicalFormFactor = wurflDevice.getCapability("physical_form_factor");
            if (physicalFormFactor != null && physicalFormFactor.equals("out_of_home_device")) {
                return 8;
            }
        } catch (CapabilityNotDefinedException | VirtualCapabilityNotDefinedException | NumberFormatException e) {
            logger.warn("Failed to determine device type from WURFL device capabilities", e);
            return null;
        }
        return null;
    }

    private String getWurflOs() {
        try {
            return wurflDevice.getVirtualCapability("advertised_device_os");
        } catch (VirtualCapabilityNotDefinedException e) {
            logger.warn("Failed to evaluate advertised device OS", e);
            return null;
        }
    }

    private String getWurflOsv() {
        try {
            return wurflDevice.getVirtualCapability("advertised_device_os_version");
        } catch (VirtualCapabilityNotDefinedException e) {
            logger.warn("Failed to evaluate advertised device OS version", e);
        }
        return null;
    }

    private Integer getWurflH() {
        try {
            return wurflDevice.getCapabilityAsInt("resolution_height");
        } catch (NumberFormatException e) {
            logger.warn("Failed to get resolution height from WURFL device capabilities", e);
            return null;
        }
    }

    private Integer getWurflW() {
        try {
            return wurflDevice.getCapabilityAsInt("resolution_width");
        } catch (NumberFormatException e) {
            logger.warn("Failed to get resolution width from WURFL device capabilities", e);
            return null;
        }
    }

    private Integer getWurflPpi() {
        try {
            return wurflDevice.getVirtualCapabilityAsInt("pixel_density");
        } catch (VirtualCapabilityNotDefinedException e) {
            logger.warn("Failed to get pixel density from WURFL device capabilities", e);
            return null;
        }
    }

    private BigDecimal getWurflPxRatio() {
        try {
            final String densityAsString = wurflDevice.getCapability("density_class");
            return densityAsString != null
                    ? new BigDecimal(densityAsString)
                    : null;
        } catch (CapabilityNotDefinedException | NumberFormatException e) {
            logger.warn("Failed to get pixel ratio from WURFL device capabilities", e);
            return null;
        }
    }

    private Integer getWurflJs() {
        try {
            return wurflDevice.getCapabilityAsBool("ajax_support_javascript") ? 1 : 0;
        } catch (CapabilityNotDefinedException | NumberFormatException e) {
            logger.warn("Failed to get JS support from WURFL device capabilities", e);
            return null;
        }
    }

    private ExtDevice updateExt(ExtDevice ortbExtDevice) {
        if (ortbExtDevice != null && ortbExtDevice.containsProperty(WURFL_PROPERTY)) {
            return ortbExtDevice;
        }

        final ExtDevice updatedExt = Optional.ofNullable(ortbExtDevice)
                .map(this::copyExtDevice)
                .orElse(ExtDevice.empty());

        updatedExt.addProperty(WURFL_PROPERTY, createWurflObject());

        return updatedExt;
    }

    private ExtDevice copyExtDevice(ExtDevice original) {
        final ExtDevice copy = ExtDevice.of(original.getAtts(), original.getPrebid());
        mapper.fillExtension(copy, original);
        return copy;
    }

    private ObjectNode createWurflObject() {
        final ObjectNode wurfl = mapper.mapper().createObjectNode();

        wurfl.put("wurfl_id", wurflDevice.getId());

        if (!addExtCaps) {
            return wurfl;
        }

        for (String capability : staticCaps) {
            try {
                final String value = wurflDevice.getCapability(capability);
                if (value != null) {
                    wurfl.put(capability, value);
                }
            } catch (Exception e) {
                logger.warn("Error getting capability for {}: {}", capability, e.getMessage());
            }
        }

        for (String virtualCapability : virtualCaps) {
            try {
                final String value = wurflDevice.getVirtualCapability(virtualCapability);
                if (value != null) {
                    wurfl.put(virtualCapability, value);
                }
            } catch (Exception e) {
                logger.warn("Could not fetch virtual capability {}", virtualCapability);
            }
        }

        return wurfl;
    }
}
