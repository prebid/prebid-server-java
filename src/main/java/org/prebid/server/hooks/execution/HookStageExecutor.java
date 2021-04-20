package org.prebid.server.hooks.execution;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountHooksConfiguration;

import java.util.List;
import java.util.Objects;

public class HookStageExecutor {

    private final ExecutionPlan executionPlan;

    private HookStageExecutor(ExecutionPlan executionPlan) {
        this.executionPlan = executionPlan;
    }

    public static HookStageExecutor create(String executionPlan, JacksonMapper mapper) {
        return new HookStageExecutor(parseExecutionPlan(executionPlan, Objects.requireNonNull(mapper)));
    }

    public Future<HookStageExecutionResult<EntrypointPayload>> executeEntrypointStage(
            MultiMap queryParams,
            MultiMap headers,
            String body,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, new EntrypointPayload() {
            @Override
            public MultiMap queryParams() {
                return queryParams;
            }

            @Override
            public MultiMap headers() {
                return headers;
            }

            @Override
            public String body() {
                return body;
            }
        }));
    }

    public Future<HookStageExecutionResult<AuctionRequestPayload>> executeRawAuctionRequestStage(
            BidRequest bidRequest,
            Account account,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, () -> bidRequest));
    }

    public Future<HookStageExecutionResult<BidderRequestPayload>> executeBidderRequestStage(
            BidderRequest bidderRequest,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, new BidderRequestPayload() {
            @Override
            public BidRequest bidRequest() {
                return bidderRequest.getBidRequest();
            }
        }));
    }

    public Future<HookStageExecutionResult<BidderResponsePayload>> executeRawBidderResponseStage(
            BidderResponse bidderResponse,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, new BidderResponsePayload() {
            @Override
            public List<BidderBid> bids() {
                return bidderResponse.getSeatBid().getBids();
            }
        }));
    }

    public Future<HookStageExecutionResult<AuctionResponsePayload>> executeAuctionResponseStage(
            BidResponse bidResponse,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, new AuctionResponsePayload() {
            @Override
            public BidResponse bidResponse() {
                return bidResponse;
            }
        }));
    }

    private static ExecutionPlan parseExecutionPlan(String executionPlan, JacksonMapper mapper) {
        if (StringUtils.isBlank(executionPlan)) {
            return ExecutionPlan.empty();
        }

        try {
            return mapper.decodeValue(executionPlan, ExecutionPlan.class);
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Hooks execution plan could not be parsed", e);
        }
    }

    private StageExecutionPlan planForEntrypointStage(Endpoint endpoint) {
        return planFor(executionPlan, endpoint, Stage.entrypoint);
    }

    private StageExecutionPlan planFor(Account account, Endpoint endpoint, Stage stage) {
        return planFor(effectiveExecutionPlanFor(account), endpoint, stage);
    }

    private static StageExecutionPlan planFor(ExecutionPlan executionPlan, Endpoint endpoint, Stage stage) {
        return executionPlan
                .getEndpoints()
                .getOrDefault(endpoint, EndpointExecutionPlan.empty())
                .getStages()
                .getOrDefault(stage, StageExecutionPlan.empty());
    }

    private ExecutionPlan effectiveExecutionPlanFor(Account account) {
        final AccountHooksConfiguration hooksAccountConfig = account.getHooks();
        final ExecutionPlan accountExecutionPlan =
                hooksAccountConfig != null ? hooksAccountConfig.getExecutionPlan() : null;

        return accountExecutionPlan != null ? accountExecutionPlan : executionPlan;
    }
}
