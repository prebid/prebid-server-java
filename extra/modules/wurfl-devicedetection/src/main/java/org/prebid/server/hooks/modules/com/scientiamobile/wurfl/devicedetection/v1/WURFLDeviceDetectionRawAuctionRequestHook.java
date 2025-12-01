package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.iab.openrtb.request.Device;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.json.JacksonMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.AuctionRequestHeadersContext;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.resolver.HeadersResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.settings.model.Account;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class WURFLDeviceDetectionRawAuctionRequestHook implements RawAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(WURFLDeviceDetectionRawAuctionRequestHook.class);

    public static final String CODE = "wurfl-devicedetection-raw-auction-request";
    private static final String WURFL_PROPERTY = "wurfl";

    private final WURFLService wurflService;
    private final Set<String> allowedPublisherIDs;
    private final boolean addExtCaps;
    private final JacksonMapper mapper;

    public WURFLDeviceDetectionRawAuctionRequestHook(WURFLService wurflService,
                                                     WURFLDeviceDetectionConfigProperties configProperties,
                                                     JacksonMapper mapper) {

        this.wurflService = Objects.requireNonNull(wurflService);
        this.addExtCaps = Objects.requireNonNull(configProperties).isExtCaps();
        this.allowedPublisherIDs = Objects.requireNonNull(configProperties.getAllowedPublisherIds());
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        if (!shouldEnrichDevice(invocationContext)) {
            return noActionResult();
        }

        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        final Device device = bidRequest.getDevice();
        if (device == null) {
            logger.warn("Device is null");
            return noActionResult();
        }

        if (isDeviceAlreadyEnriched(device)) {
            logger.info("Device is already enriched, returning original bid request");
            return noActionResult();
        }

        final Map<String, String> requestHeaders =
                invocationContext.moduleContext() instanceof AuctionRequestHeadersContext moduleContext
                        ? moduleContext.getHeaders()
                        : null;

        final Map<String, String> headers = HeadersResolver.resolve(device, requestHeaders);
        final Optional<com.scientiamobile.wurfl.core.Device> wurflDevice = wurflService.lookupDevice(headers);
        if (wurflDevice.isEmpty()) {
            logger.info("No WURFL device found, returning original bid request");
            return noActionResult();
        }

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(new OrtbDeviceUpdater(
                                wurflDevice.get(),
                                wurflService.getAllCapabilities(),
                                wurflService.getAllVirtualCapabilities(),
                                addExtCaps,
                                mapper))
                        .build());
    }

    private boolean isDeviceAlreadyEnriched(Device device) {
        final ExtDevice extDevice = device.getExt();
        if (extDevice != null && extDevice.containsProperty(WURFL_PROPERTY)) {
            return true;
        }

        // Check if other some of the other Device data are already set
        final Integer deviceType = device.getDevicetype();
        final String hwv = device.getHwv();
        return deviceType != null && deviceType > 0 && StringUtils.isNotEmpty(hwv);
    }

    private boolean shouldEnrichDevice(AuctionInvocationContext invocationContext) {
        return CollectionUtils.isEmpty(allowedPublisherIDs) || isAccountValid(invocationContext.auctionContext());
    }

    private boolean isAccountValid(AuctionContext auctionContext) {
        return Optional.ofNullable(auctionContext.getAccount())
                .map(Account::getId)
                .filter(StringUtils::isNotBlank)
                .filter(allowedPublisherIDs::contains)
                .isPresent();
    }

    private static Future<InvocationResult<AuctionRequestPayload>> noActionResult() {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
