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
import org.apache.commons.collections4.SetUtils;
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
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class LiveIntentOmniChannelIdentityProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(LoggerFactory.getLogger(
            LiveIntentOmniChannelIdentityProcessedAuctionRequestHook.class));

    private static final String CODE = "liveintent-omni-channel-identity-enrichment-hook";

    private static final String INSERTER = "s2s.liveintent.com";

    // the IdResResponse is already stamped by Ulysses with "liveintent.com" as matcher
    private static final String MATCHER = "liveintent.com";

    private final LiveIntentOmniChannelProperties config;
    private final JacksonMapper mapper;
    private final HttpClient httpClient;
    private final UserFpdActivityMask userFpdActivityMask;
    private final double logSamplingRate;
    private final Set<String> targetBidders;

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
        this.targetBidders = SetUtils.emptyIfNull(config.getTargetBidders());
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
        final IdResResponse res = mapper.decodeValue(response.getBody(), IdResResponse.class);
        final List<Eid> eids = res.getEids();

        if (CollectionUtils.isEmpty(eids)) {
            return res;
        }

        final List<Eid> modifiedEids = eids.stream()
                .map(eid -> eid.toBuilder().inserter(INSERTER).build())
                .toList();

        return IdResResponse.of(modifiedEids);
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
        return CollectionUtils.isNotEmpty(resolvedEids)
                ? AuctionRequestPayloadImpl.of(updateBidRequest(requestPayload.bidRequest(), resolvedEids))
                : requestPayload;
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, List<Eid> resolvedEids) {
        return bidRequest.toBuilder()
                .ext(updateExtRequest(bidRequest.getExt(), resolvedEids))
                .user(updateUser(bidRequest.getUser(), resolvedEids))
                .build();
    }

    private ExtRequest updateExtRequest(ExtRequest ext, List<Eid> resolvedEids) {
                if (CollectionUtils.isEmpty(resolvedEids)) {
                        return ext;
                }

        final ExtRequestPrebid extPrebid = ext != null ? ext.getPrebid() : null;
        final ExtRequestPrebidData extPrebidData = extPrebid != null ? extPrebid.getData() : null;
        final List<ExtRequestPrebidDataEidPermissions> eidPermissions =
                extPrebidData != null ? extPrebidData.getEidPermissions() : null;

        final List<ExtRequestPrebidDataEidPermissions> modifiedEidPermissions = CollectionUtils
                .isEmpty(eidPermissions)
                ? createEidPermissions()
                : modifyEidPermissions(eidPermissions);

        final ExtRequestPrebid updatedExtPrebid = Optional.ofNullable(extPrebid)
                .map(ExtRequestPrebid::toBuilder)
                .orElseGet(ExtRequestPrebid::builder)
                .data(updatePrebidData(extPrebidData, modifiedEidPermissions))
                .build();

        final ExtRequest updatedExtRequest = ExtRequest.of(updatedExtPrebid);
        if (ext != null) {
            mapper.fillExtension(updatedExtRequest, ext.getProperties());
        }

        return updatedExtRequest;
    }

    private static User updateUser(User user, List<Eid> resolvedEids) {
        final List<Eid> updatedEids = Optional.ofNullable(user)
                .map(User::getEids)
                .map(eids -> ListUtil.union(eids, resolvedEids))
                .orElse(resolvedEids);

        return Optional.ofNullable(user)
                .map(User::toBuilder)
                .orElseGet(User::builder)
                .eids(updatedEids)
                .build();
    }

    private List<ExtRequestPrebidDataEidPermissions> createEidPermissions() {
        return List.of(ExtRequestPrebidDataEidPermissions.builder()
                                .inserter(INSERTER)
                                .bidders(targetBidders.stream().toList())
                                .build());
    }

    private List<ExtRequestPrebidDataEidPermissions> modifyEidPermissions(
            List<ExtRequestPrebidDataEidPermissions> eidPermissions) {
        final List<ExtRequestPrebidDataEidPermissions> modifiedEidPermissions = eidPermissions.stream()
                                .map(this::updateEidPermission)
                                .filter(Objects::nonNull)
                                .toList();
        final List<ExtRequestPrebidDataEidPermissions> defaultEidPermissions = createEidPermissions();
        return ListUtils.union(modifiedEidPermissions, defaultEidPermissions);
    }

    private ExtRequestPrebidData updatePrebidData(ExtRequestPrebidData extPrebidData,
                                                  List<ExtRequestPrebidDataEidPermissions> eidPermissions) {

        final List<String> originalBidders = extPrebidData != null ? extPrebidData.getBidders() : null;

        return ExtRequestPrebidData.of(originalBidders, eidPermissions);
    }

    private ExtRequestPrebidDataEidPermissions updateEidPermission(ExtRequestPrebidDataEidPermissions eidPermission) {
        if (!MATCHER.equals(eidPermission.getMatcher()) || !INSERTER.equals(eidPermission.getInserter())) {
                        return eidPermission;
        }

        final List<String> allowedBidders = ListUtils.emptyIfNull(eidPermission.getBidders());
        final List<String> finalBidders = allowedBidders.stream()
                .filter(targetBidders::contains)
                .toList();

        if (CollectionUtils.isEmpty(allowedBidders) || allowedBidders.contains("*")) {
            return eidPermission.toBuilder().bidders(targetBidders.stream().toList()).build();
        }

        if (CollectionUtils.isEmpty(finalBidders)) {
            return null;
        }

        return eidPermission.toBuilder().bidders(finalBidders).build();
    }

    @Override
    public String code() {
        return CODE;
    }
}
