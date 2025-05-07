package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.IdResResponse;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config.ModuleConfig;
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
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LiveIntentOmniChannelIdentityProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(LiveIntentOmniChannelIdentityProcessedAuctionRequestHook.class);
    private static final String CODE = "liveintent-omni-channel-identity-enrichment-hook";

    private final ModuleConfig config;
    private final JacksonMapper mapper;
    private final HttpClient httpClient;

    public LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(
            ModuleConfig config,
            JacksonMapper mapper,
            HttpClient httpClient) {
        this.config = Objects.requireNonNull(config);
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        Future<InvocationResult<AuctionRequestPayload>> update = requestEnrichment(auctionRequestPayload)
                .map(resolutionResult ->
                        InvocationResultImpl.<AuctionRequestPayload>builder()
                                .status(InvocationStatus.success)
                                .action(InvocationAction.update)
                                .payloadUpdate(requestPayload -> updatedPayload(requestPayload, resolutionResult))
                                .build()
                );

        return update.onFailure(throwable -> logger.error("Failed enrichment:", throwable));
    }

    @Override
    public String code() {
        return CODE;
    }

    private AuctionRequestPayload updatedPayload(AuctionRequestPayload requestPayload, IdResResponse idResResponse) {
        BidRequest bidRequest = Optional.ofNullable(requestPayload.bidRequest()).orElse(BidRequest.builder().build());
        User user = Optional.ofNullable(bidRequest.getUser()).orElse(User.builder().build());

        List<Eid> allEids = new ArrayList<>();
        allEids.addAll(Optional.ofNullable(user.getEids()).orElse(List.of()));
        allEids.addAll(idResResponse.getEids());

        User updatedUser = user.toBuilder().eids(allEids).build();
        BidRequest updatedBidRequest = requestPayload.bidRequest().toBuilder().user(updatedUser).build();

        return AuctionRequestPayloadImpl.of(updatedBidRequest);
    }

    private Future<IdResResponse> requestEnrichment(AuctionRequestPayload auctionRequestPayload) {
        String bidRequestJson = mapper.encodeToString(auctionRequestPayload.bidRequest());
        return httpClient.post(
                        config.getIdentityResolutionEndpoint(),
                        headers(),
                        bidRequestJson,
                        config.getRequestTimeoutMs())
                .map(this::processResponse);
    }

    private IdResResponse processResponse(HttpClientResponse response) {
        return mapper.decodeValue(response.getBody(), IdResResponse.class);
    }

    private MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.AUTHORIZATION_HEADER, "Bearer " + config.getAuthToken());
    }
}
