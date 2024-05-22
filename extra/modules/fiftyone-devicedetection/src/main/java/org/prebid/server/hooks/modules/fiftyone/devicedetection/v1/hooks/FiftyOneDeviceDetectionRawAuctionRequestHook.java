package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection.DeviceRefiner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.mergers.MergingConfigurator;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.mergers.PropertyMerge;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence.CollectedEvidenceBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.AccountFilter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.adapters.DeviceMirror;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FiftyOneDeviceDetectionRawAuctionRequestHook implements RawAuctionRequestHook {

    private static final String CODE = "fiftyone-devicedetection-raw-auction-request-hook";

    private final AccountFilter accountFilter;
    private final DeviceRefiner deviceRefiner;

    public FiftyOneDeviceDetectionRawAuctionRequestHook(
            AccountFilter accountFilter,
            DeviceRefiner deviceRefiner)
    {
        this.accountFilter = accountFilter;
        this.deviceRefiner = deviceRefiner;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload payload,
            AuctionInvocationContext invocationContext)
    {
        if (!isAccountAllowed(invocationContext)) {
            return Future.succeededFuture(
                    InvocationResultImpl.<AuctionRequestPayload>builder()
                            .status(InvocationStatus.success)
                            .action(InvocationAction.no_action)
                            .moduleContext(invocationContext.moduleContext())
                            .build());
        }

        final ModuleContext moduleContext = addEvidenceToContext(
                (ModuleContext)invocationContext.moduleContext(),
                builder -> collectEvidence(builder, payload.bidRequest())
        );

        return  Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(freshPayload -> updatePayload(freshPayload, moduleContext.collectedEvidence()))
                        .moduleContext(moduleContext)
                        .build()
        );
    }

    protected boolean isAccountAllowed(AuctionInvocationContext invocationContext) {
        if (accountFilter == null) {
            return true;
        }
        final List<String> allowList = accountFilter.getAllowList();
        final boolean hasAllowList = (allowList != null && !allowList.isEmpty());
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
        } while(false);
        return !hasAllowList;
    }

    private AuctionRequestPayload updatePayload(
            AuctionRequestPayload existingPayload,
            CollectedEvidence collectedEvidence)
    {
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

        final CollectedEvidenceBuilder evidenceBuilder = collectedEvidence.toBuilder();
        collectEvidence(evidenceBuilder, bidRequest);

        EnrichmentResult<Device> mergeResult = deviceRefiner.enrichDeviceInfo(
                new DeviceMirror(existingDevice),
                evidenceBuilder.build(),
                DeviceMirror.BUILDER_METHOD_SET.makeAdapter(existingDevice));
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

    protected ModuleContext addEvidenceToContext(ModuleContext moduleContext, Consumer<CollectedEvidenceBuilder> evidenceInjector)
    {
        ModuleContext.ModuleContextBuilder contextBuilder;
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

    protected void collectEvidence(CollectedEvidenceBuilder evidenceBuilder, BidRequest bidRequest) {
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

    private static final MergingConfigurator<Map<String, String>, BrandVersion> PLATFORM_MERGER = new MergingConfigurator<>(
            List.of(
                    new PropertyMerge<>(BrandVersion::getBrand, b -> !b.isEmpty(), (evidence, platformName) ->
                            evidence.put("header.Sec-CH-UA-Platform", '"' + toHeaderSafe(platformName) + '"')
                    ),
                    new PropertyMerge<>(BrandVersion::getVersion, v -> !v.isEmpty(), (evidence, platformVersions) -> {
                        final StringBuilder s = new StringBuilder();
                        s.append('"');
                        appendVersionList(s, platformVersions);
                        s.append('"');
                        evidence.put("header.Sec-CH-UA-Platform-Version", s.toString());
                    })));

    private static final MergingConfigurator<Map<String, String>, UserAgent> AGENT_MERGER = new MergingConfigurator<>(
            List.of(
                    new PropertyMerge<>(UserAgent::getBrowsers, b -> !b.isEmpty(), (evidence, versions) -> {
                        final String fullUA = brandListToString(versions);
                        evidence.put("header.Sec-CH-UA", fullUA);
                        evidence.put("header.Sec-CH-UA-Full-Version-List", fullUA);
                    }),
                    new PropertyMerge<>(UserAgent::getPlatform, b -> true, PLATFORM_MERGER::test),
                    new PropertyMerge<>(UserAgent::getMobile, b -> true, (evidence, isMobile) ->
                            evidence.put("header.Sec-CH-UA-Mobile", "?" + isMobile)),
                    new PropertyMerge<>(UserAgent::getArchitecture, s -> !s.isEmpty(), (evidence, architecture) ->
                            evidence.put("header.Sec-CH-UA-Arch", '"' + toHeaderSafe(architecture) + '"')),
                    new PropertyMerge<>(UserAgent::getBitness, s -> !s.isEmpty(), (evidence, bitness) ->
                            evidence.put("header.Sec-CH-UA-Bitness", '"' + toHeaderSafe(bitness) + '"')),
                    new PropertyMerge<>(UserAgent::getModel, s -> !s.isEmpty(), (evidence, model) ->
                            evidence.put("header.Sec-CH-UA-Model", '"' + toHeaderSafe(model) + '"'))));

    protected void appendSecureHeaders(UserAgent userAgent, Map<String, String> evidence) {
        if (userAgent != null) {
            AGENT_MERGER.test(evidence, userAgent);
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
}
