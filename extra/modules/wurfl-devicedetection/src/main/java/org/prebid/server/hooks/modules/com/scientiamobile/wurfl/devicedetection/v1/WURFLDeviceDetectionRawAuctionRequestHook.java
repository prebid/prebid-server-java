package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
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
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class WURFLDeviceDetectionRawAuctionRequestHook implements RawAuctionRequestHook {

    public static final String CODE = "wurfl-devicedetection-raw-auction-request";

    private final WURFLService wurflService;
    private final List<String> staticCaps;
    private final List<String> virtualCaps;
    private final OrtbDeviceUpdater ortbDeviceUpdater;
    private final Map<String, String> allowedPublisherIDs;
    private final boolean addExtCaps;

    public WURFLDeviceDetectionRawAuctionRequestHook(WURFLService wurflService,
                                                     WURFLDeviceDetectionConfigProperties configProperties) {
        this.wurflService = wurflService;
        this.staticCaps = wurflService.getAllCapabilities().stream().toList();
        this.virtualCaps = wurflService.getAllVirtualCapabilities().stream().toList();
        this.ortbDeviceUpdater = new OrtbDeviceUpdater();
        this.addExtCaps = configProperties.isExtCaps();
        this.allowedPublisherIDs = configProperties.getAllowedPublisherIds().stream()
                .collect(Collectors.toMap(item -> item, item -> item));
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {
        if (!shouldEnrichDevice(invocationContext)) {
            return noUpdateResultFuture();
        }

        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        Device ortbDevice = null;
        if (bidRequest == null) {
            log.warn("BidRequest is null");
            return noUpdateResultFuture();
        } else {
            ortbDevice = bidRequest.getDevice();
            if (ortbDevice == null) {
                log.warn("Device is null");
                return noUpdateResultFuture();
            }
        }

        final AuctionRequestHeadersContext headersContext;
        Map<String, String> requestHeaders = null;
        if (invocationContext.moduleContext() instanceof AuctionRequestHeadersContext) {
            headersContext = (AuctionRequestHeadersContext) invocationContext.moduleContext();
            if (headersContext != null) {
                requestHeaders = headersContext.getHeaders();
            }

            final Map<String, String> headers = new HeadersResolver().resolve(ortbDevice, requestHeaders);
            final Optional<com.scientiamobile.wurfl.core.Device> wurflDevice = wurflService.lookupDevice(headers);
            if (wurflDevice.isEmpty()) {
                return noUpdateResultFuture();
            }

            try {
                final Device updatedDevice = ortbDeviceUpdater.update(ortbDevice, wurflDevice.get(), staticCaps,
                        virtualCaps, addExtCaps);

                return Future.succeededFuture(
                        InvocationResultImpl.<AuctionRequestPayload>builder()
                                .status(InvocationStatus.success)
                                .action(InvocationAction.update)
                                .payloadUpdate(payload ->
                                        AuctionRequestPayloadImpl.of(bidRequest.toBuilder()
                                                .device(updatedDevice)
                                                .build()))
                                .build()
                );
            } catch (Exception e) {
                log.error("Exception " + e.getMessage());
            }

        }

        return noUpdateResultFuture();
    }

    private static Future<InvocationResult<AuctionRequestPayload>> noUpdateResultFuture() {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .build());
    }

    private boolean shouldEnrichDevice(AuctionInvocationContext invocationContext) {
        if (MapUtils.isEmpty(allowedPublisherIDs)) {
            return true;
        }

        final AuctionContext auctionContext = invocationContext.auctionContext();
        return AccountValidator.builder().allowedPublisherIds(allowedPublisherIDs)
                .auctionContext(auctionContext)
                .build()
                .isAccountValid();
    }

    @Override
    public String code() {
        return CODE;
    }

}
