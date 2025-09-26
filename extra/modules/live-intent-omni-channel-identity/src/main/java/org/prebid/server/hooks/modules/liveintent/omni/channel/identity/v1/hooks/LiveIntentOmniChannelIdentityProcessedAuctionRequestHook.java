package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.IdResResponse;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config.LiveIntentOmniChannelProperties;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ListUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class LiveIntentOmniChannelIdentityProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(LoggerFactory.getLogger(
            LiveIntentOmniChannelIdentityProcessedAuctionRequestHook.class));

    private static final String CODE = "liveintent-omni-channel-identity-enrichment-hook";

    private final LiveIntentOmniChannelProperties config;
    private final JacksonMapper mapper;
    private final HttpClient httpClient;
    private final double logSamplingRate;

    public LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(LiveIntentOmniChannelProperties config,
                                                                    JacksonMapper mapper,
                                                                    HttpClient httpClient,
                                                                    double logSamplingRate) {

        this.config = Objects.requireNonNull(config);
        HttpUtil.validateUrlSyntax(config.getIdentityResolutionEndpoint());
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.logSamplingRate = logSamplingRate;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        return config.getTreatmentRate() > ThreadLocalRandom.current().nextFloat()
                ? requestIdentities(auctionRequestPayload.bidRequest())
                .<InvocationResult<AuctionRequestPayload>>map(this::update)
                .onFailure(throwable -> conditionalLogger.error(
                        "Failed enrichment: %s".formatted(throwable.getMessage()), logSamplingRate))
                : noAction();
    }

    private Future<IdResResponse> requestIdentities(BidRequest bidRequest) {
        return httpClient.post(
                        config.getIdentityResolutionEndpoint(),
                        headers(),
                        mapper.encodeToString(bidRequest),
                        config.getRequestTimeoutMs())
                .map(this::processResponse);
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
        final BidRequest bidRequest = requestPayload.bidRequest();
        final User updatedUser = Optional.ofNullable(bidRequest.getUser())
                .map(user -> user.toBuilder().eids(ListUtil.union(ListUtils.emptyIfNull(user.getEids()), eids)))
                .orElseGet(() -> User.builder().eids(eids))
                .build();

        return AuctionRequestPayloadImpl.of(bidRequest.toBuilder().user(updatedUser).build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
