package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
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
import org.prebid.server.settings.model.Account;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class WURFLDeviceDetectionRawAuctionRequestHook implements RawAuctionRequestHook {

    private static final Logger LOG = LoggerFactory.getLogger(WURFLDeviceDetectionRawAuctionRequestHook.class);
    public static final String CODE = "wurfl-devicedetection-raw-auction-request";

    private final WURFLService wurflService;
    private final OrtbDeviceUpdater ortbDeviceUpdater;
    private final Map<String, String> allowedPublisherIDs;
    private final boolean addExtCaps;

    public WURFLDeviceDetectionRawAuctionRequestHook(WURFLService wurflService,
                                                     WURFLDeviceDetectionConfigProperties configProperties) {
        this.wurflService = wurflService;
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
            LOG.warn("BidRequest is null");
            return noUpdateResultFuture();
        } else {
            ortbDevice = bidRequest.getDevice();
            if (ortbDevice == null) {
                LOG.warn("Device is null");
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
                LOG.info("No WURFL device found, returning original bid request");
                return noUpdateResultFuture();
            }

            try {
                final Device updatedDevice = ortbDeviceUpdater.update(ortbDevice, wurflDevice.get(),
                        wurflService.getAllCapabilities(),
                        wurflService.getAllVirtualCapabilities(),
                        addExtCaps);

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
                LOG.error("Exception " + e.getMessage());
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
        return isAccountValid(auctionContext);
    }

    @Override
    public String code() {
        return CODE;
    }

    private boolean isAccountValid(AuctionContext auctionContext) {

        return Optional.ofNullable(auctionContext)
                .map(AuctionContext::getAccount)
                .map(Account::getId)
                .filter(StringUtils::isNotBlank)
                .map(allowedPublisherIDs::get)
                .filter(Objects::nonNull)
                .isPresent();
    }

}
