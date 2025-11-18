package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityInvocationPayload;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.IdResResponse;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config.LiveIntentOmniChannelProperties;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.LiveIntentOmniChannelIdentityModule;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ListUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class LiveIntentOmniChannelIdentityProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(LoggerFactory.getLogger(
            LiveIntentOmniChannelIdentityProcessedAuctionRequestHook.class));

    private static final String CODE = "liveintent-omni-channel-identity-enrichment-hook";

    private final LiveIntentOmniChannelProperties config;
    private final JacksonMapper mapper;
    private final HttpClient httpClient;
    private final UserFpdActivityMask userFpdActivityMask;
    private final double logSamplingRate;
    private final List<String> targetBidders;

    public LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(LiveIntentOmniChannelProperties config,
                                                                    UserFpdActivityMask userFpdActivityMask,
                                                                    JacksonMapper mapper,
                                                                    HttpClient httpClient,
                                                                    double logSamplingRate) {

        this.config = Objects.requireNonNull(config);
        HttpUtil.validateUrlSyntax(config.getIdentityResolutionEndpoint());
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.logSamplingRate = logSamplingRate;
        this.userFpdActivityMask = Objects.requireNonNull(userFpdActivityMask);
        this.targetBidders = ListUtils.emptyIfNull(config.getTargetBidders());
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        return config.getTreatmentRate() > ThreadLocalRandom.current().nextFloat()
                ? requestIdentities(auctionRequestPayload.bidRequest(), invocationContext.auctionContext())
                .<InvocationResult<AuctionRequestPayload>>map(this::update)
                .onFailure(throwable -> conditionalLogger.error(
                        "Failed enrichment: %s".formatted(throwable.getMessage()), logSamplingRate))
                : noAction();
    }

    private Future<IdResResponse> requestIdentities(BidRequest bidRequest, AuctionContext auctionContext) {
        final BidRequest restrictedBidRequest = applyActivityRestrictions(bidRequest, auctionContext);
        return httpClient.post(
                        config.getIdentityResolutionEndpoint(),
                        headers(),
                        mapper.encodeToString(restrictedBidRequest),
                        config.getRequestTimeoutMs())
                .map(this::processResponse);
    }

    private BidRequest applyActivityRestrictions(BidRequest bidRequest, AuctionContext auctionContext) {
        final ActivityInvocationPayload activityInvocationPayload = BidRequestActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(
                        ComponentType.GENERAL_MODULE,
                        LiveIntentOmniChannelIdentityModule.CODE),
                bidRequest);
        final ActivityInfrastructure activityInfrastructure = auctionContext.getActivityInfrastructure();

        final boolean disallowTransmitUfpd = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_UFPD, activityInvocationPayload);
        final boolean disallowTransmitEids = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_EIDS, activityInvocationPayload);
        final boolean disallowTransmitGeo = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_GEO, activityInvocationPayload);
        final boolean disallowTransmitTid = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_TID, activityInvocationPayload);

        return maskUserPersonalInfo(
                bidRequest,
                disallowTransmitUfpd,
                disallowTransmitEids,
                disallowTransmitGeo,
                disallowTransmitTid);
    }

    private BidRequest maskUserPersonalInfo(BidRequest bidRequest,
                                            boolean disallowTransmitUfpd,
                                            boolean disallowTransmitEids,
                                            boolean disallowTransmitGeo,
                                            boolean disallowTransmitTid) {

        final User maskedUser = userFpdActivityMask.maskUser(
                bidRequest.getUser(), disallowTransmitUfpd, disallowTransmitEids);
        final Device maskedDevice = userFpdActivityMask.maskDevice(
                bidRequest.getDevice(), disallowTransmitUfpd, disallowTransmitGeo);

        final Source maskedSource = maskSource(bidRequest.getSource(), disallowTransmitUfpd, disallowTransmitTid);

        return bidRequest.toBuilder()
                .user(maskedUser)
                .device(maskedDevice)
                .source(maskedSource)
                .build();
    }

    private Source maskSource(Source source, boolean mastUfpd, boolean maskTid) {
        if (source == null || !(mastUfpd || maskTid)) {
            return source;
        }

        return source.toBuilder().tid(null).build();
    }

    private MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.AUTHORIZATION_HEADER, "Bearer " + config.getAuthToken());
    }

    private IdResResponse processResponse(HttpClientResponse response) {
        return mapper.decodeValue(response.getBody(), IdResResponse.class);
    }

    private static Future<InvocationResult<AuctionRequestPayload>> noAction() {
        return Future.succeededFuture(InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .build());
    }

    private InvocationResultImpl<AuctionRequestPayload> update(IdResResponse resolutionResult) {
        return InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(payload -> updatedPayload(payload, resolutionResult.getEids()))
                .analyticsTags(TagsImpl.of(List.of(
                        ActivityImpl.of(
                                "liveintent-enriched", "success",
                                List.of(
                                        ResultImpl.of(
                                                "",
                                                mapper.mapper().createObjectNode()
                                                        .put("treatmentRate", config.getTreatmentRate()),
                                                null))))))
                .build();
    }

    private AuctionRequestPayload updatedPayload(AuctionRequestPayload requestPayload, List<Eid> resolvedEids) {
        final List<Eid> eids = ListUtils.emptyIfNull(resolvedEids);
        final BidRequest bidRequest = updateAllowedBidders(requestPayload.bidRequest(), resolvedEids);
        final User updatedUser = Optional.ofNullable(bidRequest.getUser())
                .map(user -> user.toBuilder().eids(ListUtil.union(ListUtils.emptyIfNull(user.getEids()), eids)))
                .orElseGet(() -> User.builder().eids(eids))
                .build();

        return AuctionRequestPayloadImpl.of(bidRequest.toBuilder().user(updatedUser).build());
    }

    private BidRequest updateAllowedBidders(BidRequest bidRequest, List<Eid> resolvedEids) {
        if (this.targetBidders.isEmpty()) {
            return bidRequest;
        }

        final List<String> resolvedSources = resolvedEids.stream().map(Eid::getSource).distinct().toList();
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final ExtRequestPrebidData prebidData = prebid != null
                ? prebid.getData()
                : null;
        final List<ExtRequestPrebidDataEidPermissions> eidPermissions = prebidData == null
                ? List.of()
                : CollectionUtils.emptyIfNull(prebidData.getEidPermissions()).stream().toList();
        final Stream<ExtRequestPrebidDataEidPermissions> existingPermissions = eidPermissions
                .stream()
                .filter(e -> resolvedSources.contains(e.getSource()));

        final Stream<String> missingSources = resolvedSources.stream()
                .filter(src -> eidPermissions.stream().noneMatch(e -> e.getSource().equals(src)));

        final Stream<ExtRequestPrebidDataEidPermissions> additionalPermissions = eidPermissions.stream()
                .filter(e -> !resolvedSources.contains(e.getSource()));

        final List<ExtRequestPrebidDataEidPermissions> updatedPermissions = Stream.of(
                        additionalPermissions,
                        missingSources.map(src -> ExtRequestPrebidDataEidPermissions.of(src, this.targetBidders)),
                        existingPermissions.map(p -> ExtRequestPrebidDataEidPermissions.of(
                                p.getSource(),
                                Stream.concat(p.getBidders().stream(), this.targetBidders.stream())
                                        .distinct().toList())))
                .flatMap(s -> s).toList();

        final List<String> bidders = prebidData == null ? this.targetBidders : CollectionUtils.union(this.targetBidders,
                CollectionUtils.emptyIfNull(prebidData.getBidders())).stream().distinct().toList();

        final ExtRequestPrebidData updatedPrebidData = ExtRequestPrebidData.of(bidders, updatedPermissions);
        final ExtRequestPrebid updatedExtPrebid = prebid == null
                ? ExtRequestPrebid.builder().data(updatedPrebidData).build()
                : prebid.toBuilder().data(updatedPrebidData).build();

        return bidRequest.toBuilder().ext(ExtRequest.of(updatedExtPrebid)).build();
    }

    @Override
    public String code() {
        return CODE;
    }
}
