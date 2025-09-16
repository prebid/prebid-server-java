package org.prebid.server.hooks.modules.pb.request.correction.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.pb.request.correction.core.RequestCorrectionProvider;
import org.prebid.server.hooks.modules.pb.request.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.Correction;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.List;
import java.util.Objects;

public class RequestCorrectionProcessedAuctionHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "pb-request-correction-processed-auction-request";

    private final RequestCorrectionProvider requestCorrectionProvider;
    private final ObjectMapper mapper;

    public RequestCorrectionProcessedAuctionHook(RequestCorrectionProvider requestCorrectionProvider,
                                                 ObjectMapper mapper) {

        this.requestCorrectionProvider = Objects.requireNonNull(requestCorrectionProvider);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload payload,
                                                                AuctionInvocationContext context) {

        final Config config;
        try {
            config = moduleConfig(context.accountConfig());
        } catch (PreBidException e) {
            return failure(e.getMessage());
        }

        if (config == null || !config.isEnabled()) {
            return noAction();
        }

        final BidRequest bidRequest = payload.bidRequest();

        final List<Correction> corrections = requestCorrectionProvider.corrections(config, bidRequest);
        if (corrections.isEmpty()) {
            return noAction();
        }

        final InvocationResult<AuctionRequestPayload> invocationResult =
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(initialPayload -> AuctionRequestPayloadImpl.of(
                                applyCorrections(initialPayload.bidRequest(), corrections)))
                        .build();

        return Future.succeededFuture(invocationResult);
    }

    private Config moduleConfig(ObjectNode accountConfig) {
        try {
            return mapper.treeToValue(accountConfig, Config.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static BidRequest applyCorrections(BidRequest bidRequest, List<Correction> corrections) {
        BidRequest result = bidRequest;
        for (Correction correction : corrections) {
            result = correction.apply(result);
        }
        return result;
    }

    private Future<InvocationResult<AuctionRequestPayload>> failure(String message) {
        return Future.succeededFuture(InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.failure)
                .message(message)
                .action(InvocationAction.no_action)
                .build());
    }

    private static Future<InvocationResult<AuctionRequestPayload>> noAction() {
        return Future.succeededFuture(InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
