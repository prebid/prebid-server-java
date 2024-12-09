package org.prebid.server.hooks.modules.pb.response.correction.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.modules.pb.response.correction.core.ResponseCorrectionProvider;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.response.correction.core.correction.Correction;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesHook;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;

import java.util.List;
import java.util.Objects;

public class ResponseCorrectionAllProcessedBidResponsesHook implements AllProcessedBidResponsesHook {

    private static final String CODE = "pb-response-correction-all-processed-bid-responses";

    private final ResponseCorrectionProvider responseCorrectionProvider;
    private final ObjectMapper mapper;

    public ResponseCorrectionAllProcessedBidResponsesHook(ResponseCorrectionProvider responseCorrectionProvider,
                                                          ObjectMapper mapper) {
        this.responseCorrectionProvider = Objects.requireNonNull(responseCorrectionProvider);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Future<InvocationResult<AllProcessedBidResponsesPayload>> call(AllProcessedBidResponsesPayload payload,
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

        final BidRequest bidRequest = context.auctionContext().getBidRequest();

        final List<Correction> corrections = responseCorrectionProvider.corrections(config, bidRequest);
        if (corrections.isEmpty()) {
            return noAction();
        }

        final InvocationResult<AllProcessedBidResponsesPayload> invocationResult = InvocationResultImpl.<AllProcessedBidResponsesPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(initialPayload -> AllProcessedBidResponsesPayloadImpl.of(
                        applyCorrections(initialPayload.bidResponses(), config, corrections)))
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

    private static List<BidderResponse> applyCorrections(List<BidderResponse> bidderResponses, Config config, List<Correction> corrections) {
        List<BidderResponse> result = bidderResponses;
        for (Correction correction : corrections) {
            result = correction.apply(config, result);
        }
        return result;
    }

    private Future<InvocationResult<AllProcessedBidResponsesPayload>> failure(String message) {
        return Future.succeededFuture(InvocationResultImpl.<AllProcessedBidResponsesPayload>builder()
                .status(InvocationStatus.failure)
                .message(message)
                .action(InvocationAction.no_action)
                .build());
    }

    private static Future<InvocationResult<AllProcessedBidResponsesPayload>> noAction() {
        return Future.succeededFuture(InvocationResultImpl.<AllProcessedBidResponsesPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
