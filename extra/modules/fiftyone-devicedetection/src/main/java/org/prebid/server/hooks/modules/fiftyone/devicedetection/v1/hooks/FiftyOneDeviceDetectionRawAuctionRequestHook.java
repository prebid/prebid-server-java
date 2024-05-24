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

    private final ModuleConfig moduleConfig;
    private final Pipeline pipeline;

    public FiftyOneDeviceDetectionRawAuctionRequestHook(ModuleConfig moduleConfig) throws Exception {

        this.moduleConfig = moduleConfig;
        final DeviceDetectionOnPremisePipelineBuilder builder = makeBuilder();
        pipeline = builder.build();
    }

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

    protected DeviceDetectionOnPremisePipelineBuilder makeBuilder() throws Exception {

        final ModuleConfig moduleConfig = getModuleConfig();
        final DataFile dataFile = moduleConfig.getDataFile();
        final DeviceDetectionOnPremisePipelineBuilder builder = makeRawBuilder(dataFile);
        applyUpdateOptions(builder, dataFile.getUpdate());
        applyPerformanceOptions(builder, moduleConfig.getPerformance());
        PROPERTIES_USED.forEach(builder::setProperty);
        return builder;
    }

    protected DeviceDetectionOnPremisePipelineBuilder makeRawBuilder(DataFile dataFile) throws Exception {

        final Boolean shouldMakeDataCopy = dataFile.getMakeTempCopy();
        return new DeviceDetectionPipelineBuilder()
                .useOnPremise(dataFile.getPath(), shouldMakeDataCopy != null && shouldMakeDataCopy);
    }

    protected void applyPerformanceOptions(
            DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
            PerformanceConfig performanceConfig) {

        final String profile = performanceConfig.getProfile();
        if (profile != null && !profile.isEmpty()) {
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

    protected void applyUpdateOptions(
            DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
            DataFileUpdate updateConfig) {

        pipelineBuilder.setDataUpdateService(new DataUpdateServiceDefault());

        final var auto = updateConfig.getAuto();
        if (auto != null) {
            pipelineBuilder.setAutoUpdate(auto);
        }

        final var onStartup = updateConfig.getOnStartup();
        if (onStartup != null) {
            pipelineBuilder.setDataUpdateOnStartup(onStartup);
        }

        final var url = updateConfig.getUrl();
        if (url != null && !url.isEmpty()) {
            pipelineBuilder.setDataUpdateUrl(url);
        }

        final var licenseKey = updateConfig.getLicenseKey();
        if (licenseKey != null && !licenseKey.isEmpty()) {
            pipelineBuilder.setDataUpdateLicenseKey(licenseKey);
        }

        final var watchFileSystem = updateConfig.getWatchFileSystem();
        if (watchFileSystem != null) {
            pipelineBuilder.setDataFileSystemWatcher(watchFileSystem);
        }

        final var pollingInterval = updateConfig.getPollingInterval();
        if (pollingInterval != null) {
            pipelineBuilder.setUpdatePollingInterval(pollingInterval);
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
        if (accountFilter == null) {
            return true;
        }
        final List<String> allowList = accountFilter.getAllowList();
        final boolean hasAllowList = allowList != null && !allowList.isEmpty();
        do {
            if (invocationContext == null) {
                break;
            }
            final AuctionContext auctionContext = invocationContext.auctionContext();
            if (auctionContext == null) {
                break;
            }
            final Account account = auctionContext.getAccount();
            if (account == null) {
                break;
            }
            final String accountId = account.getId();
            if (accountId == null || accountId.isEmpty()) {
                break;
            }
            if (hasAllowList) {
                return allowList.contains(accountId);
            }
        } while (false);
        return !hasAllowList;
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
        if (!(currentMake != null && !currentMake.isEmpty())) {
            final String make = getSafe(deviceData, DeviceData::getHardwareVendor);
            if (make != null && !make.isEmpty()) {
                deviceBuilder.make(make);
                updatedFields.add("make");
            }
        }

        final String currentModel = device.getModel();
        if (!(currentModel != null && !currentModel.isEmpty())) {
            final String model = getSafe(deviceData, DeviceData::getHardwareModel);
            if (model != null && !model.isEmpty()) {
                deviceBuilder.model(model);
                updatedFields.add("model");
            } else {
                final List<String> names = getSafe(deviceData, DeviceData::getHardwareName);
                if (names != null && !names.isEmpty()) {
                    deviceBuilder.model(String.join(",", names));
                    updatedFields.add("model");
                }
            }
        }

        final String currentOS = device.getOs();
        if (!(currentOS != null && !currentOS.isEmpty())) {
            final String os = getSafe(deviceData, DeviceData::getPlatformName);
            if (os != null && !os.isEmpty()) {
                deviceBuilder.os(os);
                updatedFields.add("os");
            }
        }

        final String currentOSV = device.getOsv();
        if (!(currentOSV != null && !currentOSV.isEmpty())) {
            final String osv = getSafe(deviceData, DeviceData::getPlatformVersion);
            if (osv != null && !osv.isEmpty()) {
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
        if (!(currentDeviceId != null && !currentDeviceId.isEmpty())) {
            final String deviceID = getSafe(deviceData, DeviceData::getDeviceId);
            if (deviceID != null && !deviceID.isEmpty()) {
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

    protected Integer convertDeviceType(String deviceType) {

        return Optional
                .ofNullable(MAPPING.get(deviceType))
                .orElse(ORTBDeviceType.UNKNOWN.ordinal());
    }

    protected Map<String, String> pickRelevantFrom(CollectedEvidence collectedEvidence) {

        final Map<String, String> evidence = new HashMap<>();

        final String ua = collectedEvidence.deviceUA();
        if (ua != null && !ua.isEmpty()) {
            evidence.put("header.user-agent", ua);
        }
        final Map<String, String> secureHeaders = collectedEvidence.secureHeaders();
        if (secureHeaders != null && !secureHeaders.isEmpty()) {
            evidence.putAll(secureHeaders);
        }
        if (!evidence.isEmpty()) {
            return evidence;
        }

        final Collection<Map.Entry<String, String>> headers = collectedEvidence.rawHeaders();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> nextRawHeader : headers) {
                evidence.put("header." + nextRawHeader.getKey(), nextRawHeader.getValue());
            }
        }

        return evidence;
    }

    protected ModuleContext addEvidenceToContext(
            ModuleContext moduleContext,
            Consumer<CollectedEvidence.CollectedEvidenceBuilder> evidenceInjector) {

        final ModuleContext.ModuleContextBuilder contextBuilder;
        CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder = null;
        if (moduleContext != null) {
            contextBuilder = moduleContext.toBuilder();
            final CollectedEvidence lastEvidence = moduleContext.collectedEvidence();
            if (lastEvidence != null) {
                evidenceBuilder = lastEvidence.toBuilder();
            }
        } else {
            contextBuilder = ModuleContext.builder();
        }
        if (evidenceBuilder == null) {
            evidenceBuilder = CollectedEvidence.builder();
        }
        evidenceInjector.accept(evidenceBuilder);
        return contextBuilder
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
        if (versions != null && !versions.isEmpty()) {
            final String fullUA = brandListToString(versions);
            evidence.put("header.Sec-CH-UA", fullUA);
            evidence.put("header.Sec-CH-UA-Full-Version-List", fullUA);
        }

        final BrandVersion platform = userAgent.getPlatform();
        if (platform != null) {
            final String platformName = platform.getBrand();
            if (platformName != null && !platformName.isEmpty()) {
                evidence.put("header.Sec-CH-UA-Platform", '"' + toHeaderSafe(platformName) + '"');
            }

            final List<String> platformVersions = platform.getVersion();
            if (platformVersions != null && !platformVersions.isEmpty()) {
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
        if (architecture != null && !architecture.isEmpty()) {
            evidence.put("header.Sec-CH-UA-Arch", '"' + toHeaderSafe(architecture) + '"');
        }

        final String bitness = userAgent.getBitness();
        if (bitness != null && !bitness.isEmpty()) {
            evidence.put("header.Sec-CH-UA-Bitness", '"' + toHeaderSafe(bitness) + '"');
        }

        final String model = userAgent.getModel();
        if (model != null && !model.isEmpty()) {
            evidence.put("header.Sec-CH-UA-Model", '"' + toHeaderSafe(model) + '"');
        }
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

        if (versions == null || versions.isEmpty()) {
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

    private static String toHeaderSafe(String rawValue) {

        return rawValue.replace("\"", "\\\"");
    }

    public static final String EXT_DEVICE_ID_KEY = "fiftyonedegrees_deviceId";

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

    @Builder
    public record EnrichmentResult(
            Device enrichedDevice,
            Collection<String> enrichedFields,
            Exception processingException
    ) {
    }

    protected ModuleConfig getModuleConfig() {

        return moduleConfig;
    }

    protected Pipeline getPipeline() {

        return pipeline;
    }
}
