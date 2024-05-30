package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.devicedetection.DeviceDetectionPipelineBuilder;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.engines.Constants;
import fiftyone.pipeline.engines.data.AspectPropertyValue;
import fiftyone.pipeline.engines.services.DataUpdateServiceDefault;
import lombok.Builder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.AccountFilter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import io.vertx.core.Future;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class FiftyOneDeviceDetectionRawAuctionRequestHook implements RawAuctionRequestHook {

    private static final String CODE = "fiftyone-devicedetection-raw-auction-request-hook";

    public static final String EXT_DEVICE_ID_KEY = "fiftyonedegrees_deviceId";

    private static final Collection<String> PROPERTIES_USED = List.of(
            "devicetype",
            "hardwarevendor",
            "hardwaremodel",
            "hardwarename",
            "platformname",
            "platformversion",
            "screenpixelsheight",
            "screenpixelswidth",
            "screeninchesheight",
            "pixelratio",

            "BrowserName",
            "BrowserVersion",
            "IsCrawler",

            "BrowserVendor",
            "PlatformVendor",
            "Javascript",
            "GeoLocation",
            "HardwareModelVariants");

    // https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/main/AdCOM%20v1.0%20FINAL.md#list--device-types-
    private enum ORTBDeviceType {
        UNKNOWN,
        MOBILE_TABLET,
        PERSONAL_COMPUTER,
        CONNECTED_TV,
        PHONE,
        TABLET,
        CONNECTED_DEVICE,
        SET_TOP_BOX,
        OOH_DEVICE
    }

    private static final Map<String, Integer> MAPPING = Map.ofEntries(
            Map.entry("Phone", ORTBDeviceType.PHONE.ordinal()),
            Map.entry("Console", ORTBDeviceType.SET_TOP_BOX.ordinal()),
            Map.entry("Desktop", ORTBDeviceType.PERSONAL_COMPUTER.ordinal()),
            Map.entry("EReader", ORTBDeviceType.PERSONAL_COMPUTER.ordinal()),
            Map.entry("IoT", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("Kiosk", ORTBDeviceType.OOH_DEVICE.ordinal()),
            Map.entry("MediaHub", ORTBDeviceType.SET_TOP_BOX.ordinal()),
            Map.entry("Mobile", ORTBDeviceType.MOBILE_TABLET.ordinal()),
            Map.entry("Router", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("SmallScreen", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("SmartPhone", ORTBDeviceType.MOBILE_TABLET.ordinal()),
            Map.entry("SmartSpeaker", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("SmartWatch", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            Map.entry("Tablet", ORTBDeviceType.TABLET.ordinal()),
            Map.entry("Tv", ORTBDeviceType.CONNECTED_TV.ordinal()),
            Map.entry("Vehicle Display", ORTBDeviceType.PERSONAL_COMPUTER.ordinal())
    );

    private final ModuleConfig moduleConfig;
    private final Pipeline pipeline;

    public FiftyOneDeviceDetectionRawAuctionRequestHook(ModuleConfig moduleConfig) throws Exception {

        this.moduleConfig = moduleConfig;
        final DeviceDetectionOnPremisePipelineBuilder builder = makeBuilder();
        pipeline = builder.build();
    }

    protected DeviceDetectionOnPremisePipelineBuilder makeBuilder() throws Exception {

        final ModuleConfig moduleConfig = getModuleConfig();
        final DataFile dataFile = moduleConfig.getDataFile();
        final DeviceDetectionOnPremisePipelineBuilder builder = makeRawBuilder(dataFile);
        applyUpdateOptions(builder, dataFile.getUpdate());
        applyPerformanceOptions(builder, moduleConfig.getPerformance());
        PROPERTIES_USED.forEach(builder::setProperty);
        return builder;
    }

    protected ModuleConfig getModuleConfig() {

        return moduleConfig;
    }

    protected DeviceDetectionOnPremisePipelineBuilder makeRawBuilder(DataFile dataFile) throws Exception {

        final Boolean shouldMakeDataCopy = dataFile.getMakeTempCopy();
        return new DeviceDetectionPipelineBuilder()
                .useOnPremise(dataFile.getPath(), shouldMakeDataCopy != null && shouldMakeDataCopy);
    }

    protected void applyUpdateOptions(
            DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
            DataFileUpdate updateConfig) {

        pipelineBuilder.setDataUpdateService(new DataUpdateServiceDefault());

        final Boolean auto = updateConfig.getAuto();
        if (auto != null) {
            pipelineBuilder.setAutoUpdate(auto);
        }

        final Boolean onStartup = updateConfig.getOnStartup();
        if (onStartup != null) {
            pipelineBuilder.setDataUpdateOnStartup(onStartup);
        }

        final String url = updateConfig.getUrl();
        if (StringUtils.isNotBlank(url)) {
            pipelineBuilder.setDataUpdateUrl(url);
        }

        final String licenseKey = updateConfig.getLicenseKey();
        if (StringUtils.isNotBlank(licenseKey)) {
            pipelineBuilder.setDataUpdateLicenseKey(licenseKey);
        }

        final Boolean watchFileSystem = updateConfig.getWatchFileSystem();
        if (watchFileSystem != null) {
            pipelineBuilder.setDataFileSystemWatcher(watchFileSystem);
        }

        final Integer pollingInterval = updateConfig.getPollingInterval();
        if (pollingInterval != null) {
            pipelineBuilder.setUpdatePollingInterval(pollingInterval);
        }
    }

    protected void applyPerformanceOptions(
            DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
            PerformanceConfig performanceConfig) {

        final String profile = performanceConfig.getProfile();
        if (StringUtils.isNotBlank(profile)) {
            final String lowercasedProfile = profile.toLowerCase();
            for (Constants.PerformanceProfiles nextProfile: Constants.PerformanceProfiles.values()) {
                if (nextProfile.name().toLowerCase().equals(lowercasedProfile)) {
                    pipelineBuilder.setPerformanceProfile(nextProfile);
                    return;
                }
            }
        }

        final Integer concurrency = performanceConfig.getConcurrency();
        if (concurrency != null) {
            pipelineBuilder.setConcurrency(concurrency);
        }

        final Integer difference = performanceConfig.getDifference();
        if (difference != null) {
            pipelineBuilder.setDifference(difference);
        }

        final Boolean allowUnmatched = performanceConfig.getAllowUnmatched();
        if (allowUnmatched != null) {
            pipelineBuilder.setAllowUnmatched(allowUnmatched);
        }

        final Integer drift = performanceConfig.getDrift();
        if (drift != null) {
            pipelineBuilder.setDrift(drift);
        }
    }

    @Override
    public String code() {

        return CODE;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload payload,
            AuctionInvocationContext invocationContext) {

        if (!isAccountAllowed(invocationContext)) {
            return Future.succeededFuture(
                    InvocationResultImpl.<AuctionRequestPayload>builder()
                            .status(InvocationStatus.success)
                            .action(InvocationAction.no_action)
                            .moduleContext(invocationContext.moduleContext())
                            .build());
        }

        final ModuleContext moduleContext = addEvidenceToContext(
                (ModuleContext) invocationContext.moduleContext(),
                builder -> collectEvidence(builder, payload.bidRequest())
        );

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(freshPayload -> updatePayload(freshPayload, moduleContext.collectedEvidence()))
                        .moduleContext(moduleContext)
                        .build()
        );
    }

    protected boolean isAccountAllowed(AuctionInvocationContext invocationContext) {

        final AccountFilter accountFilter = getModuleConfig().getAccountFilter();
        final List<String> allowList = ObjectUtil.getIfNotNull(accountFilter, AccountFilter::getAllowList);
        if (CollectionUtils.isEmpty(allowList)) {
            return true;
        }
        return Optional.ofNullable(invocationContext)
                .map(AuctionInvocationContext::auctionContext)
                .map(AuctionContext::getAccount)
                .map(Account::getId)
                .filter(StringUtils::isNotBlank)
                .map(allowList::contains)
                .orElse(false);
    }

    protected ModuleContext addEvidenceToContext(
            ModuleContext moduleContext,
            Consumer<CollectedEvidence.CollectedEvidenceBuilder> evidenceInjector) {

        final CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder = Optional.ofNullable(moduleContext)
                .map(ModuleContext::collectedEvidence)
                .map(CollectedEvidence::toBuilder)
                .orElseGet(CollectedEvidence::builder);

        evidenceInjector.accept(evidenceBuilder);

        return Optional.ofNullable(moduleContext)
                .map(ModuleContext::toBuilder)
                .orElseGet(ModuleContext::builder)
                .collectedEvidence(evidenceBuilder.build())
                .build();
    }

    protected void collectEvidence(
            CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder,
            BidRequest bidRequest) {

        final Device device = bidRequest.getDevice();
        if (device == null) {
            return;
        }
        final String ua = device.getUa();
        if (ua != null) {
            evidenceBuilder.deviceUA(ua);
        }
        final UserAgent sua = device.getSua();
        if (sua != null) {
            final HashMap<String, String> secureHeaders = new HashMap<>();
            appendSecureHeaders(sua, secureHeaders);
            evidenceBuilder.secureHeaders(secureHeaders);
        }
    }

    protected void appendSecureHeaders(UserAgent userAgent, Map<String, String> evidence) {

        if (userAgent == null) {
            return;
        }

        final List<BrandVersion> versions = userAgent.getBrowsers();
        if (CollectionUtils.isNotEmpty(versions)) {
            final String fullUA = brandListToString(versions);
            evidence.put("header.Sec-CH-UA", fullUA);
            evidence.put("header.Sec-CH-UA-Full-Version-List", fullUA);
        }

        final BrandVersion platform = userAgent.getPlatform();
        if (platform != null) {
            final String platformName = platform.getBrand();
            if (StringUtils.isNotBlank(platformName)) {
                evidence.put("header.Sec-CH-UA-Platform", '"' + toHeaderSafe(platformName) + '"');
            }

            final List<String> platformVersions = platform.getVersion();
            if (CollectionUtils.isNotEmpty(platformVersions)) {
                final StringBuilder s = new StringBuilder();
                s.append('"');
                appendVersionList(s, platformVersions);
                s.append('"');
                evidence.put("header.Sec-CH-UA-Platform-Version", s.toString());
            }
        }

        final Integer isMobile = userAgent.getMobile();
        if (isMobile != null) {
            evidence.put("header.Sec-CH-UA-Mobile", "?" + isMobile);
        }

        final String architecture = userAgent.getArchitecture();
        if (StringUtils.isNotBlank(architecture)) {
            evidence.put("header.Sec-CH-UA-Arch", '"' + toHeaderSafe(architecture) + '"');
        }

        final String bitness = userAgent.getBitness();
        if (StringUtils.isNotBlank(bitness)) {
            evidence.put("header.Sec-CH-UA-Bitness", '"' + toHeaderSafe(bitness) + '"');
        }

        final String model = userAgent.getModel();
        if (StringUtils.isNotBlank(model)) {
            evidence.put("header.Sec-CH-UA-Model", '"' + toHeaderSafe(model) + '"');
        }
    }

    private static String toHeaderSafe(String rawValue) {

        return rawValue.replace("\"", "\\\"");
    }

    private static String brandListToString(List<BrandVersion> versions) {

        final StringBuilder s = new StringBuilder();
        for (BrandVersion nextBrandVersion : versions) {
            final String brandName = nextBrandVersion.getBrand();
            if (brandName == null) {
                continue;
            }
            if (!s.isEmpty()) {
                s.append(", ");
            }
            s.append('"');
            s.append(toHeaderSafe(brandName));
            s.append("\";v=\"");
            appendVersionList(s, nextBrandVersion.getVersion());
            s.append('"');
        }
        return s.toString();
    }

    private static void appendVersionList(StringBuilder s, List<String> versions) {

        if (CollectionUtils.isEmpty(versions)) {
            return;
        }
        boolean isFirstVersionFragment = true;
        for (String nextFragment : versions) {
            if (!isFirstVersionFragment) {
                s.append('.');
            }
            s.append(nextFragment);
            isFirstVersionFragment = false;
        }
    }

    private AuctionRequestPayload updatePayload(
            AuctionRequestPayload existingPayload,
            CollectedEvidence collectedEvidence) {

        final BidRequest currentRequest = existingPayload.bidRequest();
        final BidRequest patchedRequest = enrichDevice(currentRequest, collectedEvidence);
        if (patchedRequest == null || patchedRequest == currentRequest) {
            return existingPayload;
        }
        return AuctionRequestPayloadImpl.of(patchedRequest);
    }

    protected BidRequest enrichDevice(BidRequest bidRequest, CollectedEvidence collectedEvidence) {

        if (bidRequest == null) {
            return null;
        }
        final Device existingDevice = ObjectUtil.firstNonNull(bidRequest::getDevice, () -> Device.builder().build());

        final CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder = collectedEvidence.toBuilder();
        collectEvidence(evidenceBuilder, bidRequest);

        final EnrichmentResult mergeResult = populateDeviceInfo(existingDevice, evidenceBuilder.build());
        if (mergeResult == null) {
            return null;
        }

        final Device mergedDevice = mergeResult.enrichedDevice();
        if (mergedDevice == null) {
            return null;
        }

        return bidRequest.toBuilder()
                .device(mergeResult.enrichedDevice())
                .build();
    }

    @Builder
    public record EnrichmentResult(
            Device enrichedDevice,
            Collection<String> enrichedFields,
            Exception processingException
    ) {
    }

    protected EnrichmentResult populateDeviceInfo(
            Device device,
            CollectedEvidence collectedEvidence) {

        try (FlowData data = getPipeline().createFlowData()) {
            data.addEvidence(pickRelevantFrom(collectedEvidence));
            data.process();
            final DeviceData deviceData = data.get(DeviceData.class);
            if (device == null) {
                return null;
            }
            return patchDevice(device, deviceData);
        } catch (Exception e) {
            return EnrichmentResult
                    .builder()
                    .processingException(e)
                    .build();
        }
    }

    protected Pipeline getPipeline() {

        return pipeline;
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

        final Collection<Map.Entry<String, String>> headers = collectedEvidence.rawHeaders();
        if (CollectionUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, String> nextRawHeader : headers) {
                evidence.put("header." + nextRawHeader.getKey(), nextRawHeader.getValue());
            }
        }

        return evidence;
    }

    protected EnrichmentResult patchDevice(Device device, DeviceData deviceData) {

        final List<String> updatedFields = new ArrayList<>();
        final Device.DeviceBuilder deviceBuilder = device.toBuilder();

        final Integer currentDeviceType = device.getDevicetype();
        if (!(currentDeviceType != null && currentDeviceType > 0)) {
            final String rawDeviceType = getSafe(deviceData, DeviceData::getDeviceType);
            if (rawDeviceType != null) {
                final Integer properDeviceType = convertDeviceType(rawDeviceType);
                if (properDeviceType != null && properDeviceType > 0) {
                    deviceBuilder.devicetype(properDeviceType);
                    updatedFields.add("devicetype");
                }
            }
        }

        final String currentMake = device.getMake();
        if (!(StringUtils.isNotBlank(currentMake))) {
            final String make = getSafe(deviceData, DeviceData::getHardwareVendor);
            if (StringUtils.isNotBlank(make)) {
                deviceBuilder.make(make);
                updatedFields.add("make");
            }
        }

        final String currentModel = device.getModel();
        if (!(StringUtils.isNotBlank(currentModel))) {
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

        final String currentOS = device.getOs();
        if (!(StringUtils.isNotBlank(currentOS))) {
            final String os = getSafe(deviceData, DeviceData::getPlatformName);
            if (StringUtils.isNotBlank(os)) {
                deviceBuilder.os(os);
                updatedFields.add("os");
            }
        }

        final String currentOSV = device.getOsv();
        if (!(StringUtils.isNotBlank(currentOSV))) {
            final String osv = getSafe(deviceData, DeviceData::getPlatformVersion);
            if (StringUtils.isNotBlank(osv)) {
                deviceBuilder.osv(osv);
                updatedFields.add("osv");
            }
        }

        final Integer currentH = device.getH();
        if (!(currentH != null && currentH > 0)) {
            final Integer h = getSafe(deviceData, DeviceData::getScreenPixelsHeight);
            if (h != null && h > 0) {
                deviceBuilder.h(h);
                updatedFields.add("h");
            }
        }

        final Integer currentW = device.getW();
        if (!(currentW != null && currentW > 0)) {
            final Integer w = getSafe(deviceData, DeviceData::getScreenPixelsWidth);
            if (w != null && w > 0) {
                deviceBuilder.w(w);
                updatedFields.add("w");
            }
        }

        final Integer currentPPI = device.getPpi();
        if (!(currentPPI != null && currentPPI > 0)) {
            final Integer pixelsHeight = getSafe(deviceData, DeviceData::getScreenPixelsHeight);
            if (pixelsHeight != null) {
                final Double inchesHeight = getSafe(deviceData, DeviceData::getScreenInchesHeight);
                if (!(inchesHeight == null || inchesHeight == 0)) {
                    deviceBuilder.ppi((int) Math.round(pixelsHeight / inchesHeight));
                    updatedFields.add("ppi");
                }
            }
        }

        final BigDecimal currentPixelRatio = device.getPxratio();
        if (!(currentPixelRatio != null && currentPixelRatio.intValue() > 0)) {
            final Double rawRatio = getSafe(deviceData, DeviceData::getPixelRatio);
            if (rawRatio != null && rawRatio > 0) {
                deviceBuilder.pxratio(BigDecimal.valueOf(rawRatio));
                updatedFields.add("pxratio");
            }
        }

        final String currentDeviceId = getDeviceId(device);
        if (!(StringUtils.isNotBlank(currentDeviceId))) {
            final String deviceID = getSafe(deviceData, DeviceData::getDeviceId);
            if (StringUtils.isNotBlank(deviceID)) {
                setDeviceId(deviceBuilder, device, deviceID);
                updatedFields.add("ext." + EXT_DEVICE_ID_KEY);
            }
        }

        if (updatedFields.isEmpty()) {
            return null;
        }

        return EnrichmentResult
                .builder()
                .enrichedDevice(deviceBuilder.build())
                .enrichedFields(updatedFields)
                .build();
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

        return Optional
                .ofNullable(MAPPING.get(deviceType))
                .orElse(ORTBDeviceType.UNKNOWN.ordinal());
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
     * @see fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceId()
     *
     * @param deviceBuilder Writable builder to save device ID into.
     * @param device Raw (non-builder) form of device before modification.
     * @param deviceId New Device ID value.
     */
    public static void setDeviceId(Device.DeviceBuilder deviceBuilder, Device device, String deviceId) {

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
}
