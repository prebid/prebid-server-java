package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.AccountFilter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.SecureHeadersRetriever;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import io.vertx.core.Future;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.ObjectUtil;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class FiftyOneDeviceDetectionRawAuctionRequestHook implements RawAuctionRequestHook {
    private static final String CODE = "fiftyone-devicedetection-raw-auction-request-hook";

    private final AccountFilter accountFilter;
    private final DeviceEnricher deviceEnricher;

    public FiftyOneDeviceDetectionRawAuctionRequestHook(@Nonnull AccountFilter accountFilter,
                                                        @Nonnull DeviceEnricher deviceEnricher) {
        this.accountFilter = Objects.requireNonNull(accountFilter);
        this.deviceEnricher = Objects.requireNonNull(deviceEnricher);
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload payload,
                                                                AuctionInvocationContext invocationContext) {
        final ModuleContext oldModuleContext = (ModuleContext) ObjectUtil.getIfNotNull(
                invocationContext,
                AuctionInvocationContext::moduleContext);

        if (!isAccountAllowed(invocationContext)) {
            return Future.succeededFuture(
                    InvocationResultImpl.<AuctionRequestPayload>builder()
                            .status(InvocationStatus.success)
                            .action(InvocationAction.no_action)
                            .moduleContext(oldModuleContext)
                            .build());
        }

        final ModuleContext moduleContext = addEvidenceToContext(
                oldModuleContext,
                builder -> collectEvidence(builder, payload.bidRequest()));

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(freshPayload -> updatePayload(freshPayload, moduleContext.collectedEvidence()))
                        .moduleContext(moduleContext)
                        .build()
        );
    }

    private boolean isAccountAllowed(AuctionInvocationContext invocationContext) {
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

    private ModuleContext addEvidenceToContext(ModuleContext moduleContext,
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

    private void collectEvidence(CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder, BidRequest bidRequest) {
        final Device device = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getDevice);
        if (device == null) {
            return;
        }
        final String ua = device.getUa();
        if (ua != null) {
            evidenceBuilder.deviceUA(ua);
        }
        final UserAgent sua = device.getSua();
        if (sua != null) {
            evidenceBuilder.secureHeaders(convertSecureHeaders(sua));
        }
    }

    private Map<String, String> convertSecureHeaders(UserAgent userAgent) {
        return SecureHeadersRetriever.retrieveFrom(userAgent);
    }

    private AuctionRequestPayload updatePayload(AuctionRequestPayload existingPayload,
                                                CollectedEvidence collectedEvidence) {
        final BidRequest currentRequest = existingPayload.bidRequest();
        final BidRequest patchedRequest = enrichDevice(currentRequest, collectedEvidence);
        if (patchedRequest == null || patchedRequest == currentRequest) {
            return existingPayload;
        }
        return AuctionRequestPayloadImpl.of(patchedRequest);
    }

    private BidRequest enrichDevice(BidRequest bidRequest, CollectedEvidence collectedEvidence) {
        if (bidRequest == null) {
            return null;
        }

        final CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder = collectedEvidence.toBuilder();
        collectEvidence(evidenceBuilder, bidRequest);

        final EnrichmentResult mergeResult = deviceEnricher.populateDeviceInfo(
                bidRequest.getDevice(),
                evidenceBuilder.build());
        return Optional.ofNullable(mergeResult)
                .map(EnrichmentResult::enrichedDevice)
                .map(mergedDevice -> bidRequest.toBuilder().device(mergedDevice).build())
                .orElse(null);
    }
}
