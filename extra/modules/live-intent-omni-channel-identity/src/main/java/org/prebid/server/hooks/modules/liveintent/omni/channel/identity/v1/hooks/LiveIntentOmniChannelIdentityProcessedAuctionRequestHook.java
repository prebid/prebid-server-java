package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
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
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ListUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

public class LiveIntentOmniChannelIdentityProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(
            LiveIntentOmniChannelIdentityProcessedAuctionRequestHook.class);
    private static final String CODE = "liveintent-omni-channel-identity-enrichment-hook";

    private final LiveIntentOmniChannelProperties config;
    private final JacksonMapper mapper;
    private final HttpClient httpClient;
    private final RandomGenerator random;

    public LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(LiveIntentOmniChannelProperties config,
                                                                    JacksonMapper mapper,
                                                                    HttpClient httpClient,
                                                                    RandomGenerator random) {

        this.config = Objects.requireNonNull(config);
        //todo: maybe it's redundant, what do you think?
        HttpUtil.validateUrlSyntax(config.getIdentityResolutionEndpoint());
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        return config.getTreatmentRate() <= random.nextFloat()
                ? noAction()
                : requestIdentities(auctionRequestPayload.bidRequest())
                .<InvocationResult<AuctionRequestPayload>>map(this::update)
                 //todo: is it find to just fail instead of rejection or no_action?
                .onFailure(throwable -> logger.error("Failed enrichment:", throwable));

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

    //todo: no status check and proper error code handling
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
                //todo: might eids be null? NPE is possible
                .payloadUpdate(payload -> updatedPayload(payload, resolutionResult.getEids()))
                .build();
    }

    private AuctionRequestPayload updatedPayload(AuctionRequestPayload requestPayload, List<Eid> resolvedEids) {
        final BidRequest bidRequest = requestPayload.bidRequest();
        final User updatedUser = Optional.ofNullable(bidRequest.getUser())
                .map(user -> user.toBuilder().eids(user.getEids() == null
                        ? resolvedEids
                        : ListUtil.union(user.getEids(), resolvedEids)))
                .orElseGet(() -> User.builder().eids(resolvedEids))
                .build();

        return AuctionRequestPayloadImpl.of(bidRequest.toBuilder().user(updatedUser).build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
