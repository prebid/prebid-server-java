package org.prebid.server.hooks.execution;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.MultiMap;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountHooksConfiguration;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HookStageExecutor {

    private static final String ENTITY_HTTP_REQUEST = "http-request";
    private static final String ENTITY_AUCTION_REQUEST = "auction-request";
    private static final String ENTITY_AUCTION_RESPONSE = "auction-response";

    private final ExecutionPlan hostExecutionPlan;
    private final ExecutionPlan defaultAccountExecutionPlan;
    private final HookCatalog hookCatalog;
    private final TimeoutFactory timeoutFactory;
    private final Vertx vertx;
    private final Clock clock;

    private HookStageExecutor(ExecutionPlan hostExecutionPlan,
                              ExecutionPlan defaultAccountExecutionPlan,
                              HookCatalog hookCatalog,
                              TimeoutFactory timeoutFactory,
                              Vertx vertx,
                              Clock clock) {

        this.hostExecutionPlan = hostExecutionPlan;
        this.defaultAccountExecutionPlan = defaultAccountExecutionPlan;
        this.hookCatalog = hookCatalog;
        this.timeoutFactory = timeoutFactory;
        this.vertx = vertx;
        this.clock = clock;
    }

    public static HookStageExecutor create(String hostExecutionPlan,
                                           String defaultAccountExecutionPlan,
                                           HookCatalog hookCatalog,
                                           TimeoutFactory timeoutFactory,
                                           Vertx vertx,
                                           Clock clock,
                                           JacksonMapper mapper) {

        return new HookStageExecutor(
                parseExecutionPlan(hostExecutionPlan, Objects.requireNonNull(mapper)),
                parseExecutionPlan(defaultAccountExecutionPlan, mapper),
                Objects.requireNonNull(hookCatalog),
                Objects.requireNonNull(timeoutFactory),
                Objects.requireNonNull(vertx),
                Objects.requireNonNull(clock));
    }

    public Future<HookStageExecutionResult<EntrypointPayload>> executeEntrypointStage(
            MultiMap queryParams,
            MultiMap headers,
            String body,
            HookExecutionContext context) {

        final Endpoint endpoint = context.getEndpoint();

        return this.<EntrypointPayload, InvocationContext>stageExecutor(Stage.entrypoint, ENTITY_HTTP_REQUEST, context)
                .withExecutionPlan(planForEntrypointStage(endpoint))
                .withInitialPayload(EntrypointPayloadImpl.of(queryParams, headers, body))
                .withHookProvider(hookId ->
                        hookCatalog.entrypointHookBy(hookId.getModuleCode(), hookId.getHookImplCode()))
                .withInvocationContextProvider(invocationContextProvider(endpoint))
                .withRejectAllowed(true)
                .execute();
    }

    public Future<HookStageExecutionResult<AuctionRequestPayload>> executeRawAuctionRequestStage(
            AuctionContext auctionContext) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final Endpoint endpoint = context.getEndpoint();
        final Stage stage = Stage.raw_auction_request;

        return this
                .<AuctionRequestPayload, AuctionInvocationContext>stageExecutor(
                        stage, ENTITY_AUCTION_REQUEST, context, account, endpoint)
                .withInitialPayload(AuctionRequestPayloadImpl.of(bidRequest))
                .withHookProvider(hookId ->
                        hookCatalog.rawAuctionRequestHookBy(hookId.getModuleCode(), hookId.getHookImplCode()))
                .withInvocationContextProvider(auctionInvocationContextProvider(endpoint, auctionContext))
                .withRejectAllowed(true)
                .execute();
    }

    public Future<HookStageExecutionResult<AuctionRequestPayload>> executeProcessedAuctionRequestStage(
            AuctionContext auctionContext) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final Endpoint endpoint = context.getEndpoint();
        final Stage stage = Stage.processed_auction_request;

        return this
                .<AuctionRequestPayload, AuctionInvocationContext>stageExecutor(
                        stage, ENTITY_AUCTION_REQUEST, context, account, endpoint)
                .withInitialPayload(AuctionRequestPayloadImpl.of(bidRequest))
                .withHookProvider(hookId ->
                        hookCatalog.processedAuctionRequestHookBy(hookId.getModuleCode(), hookId.getHookImplCode()))
                .withInvocationContextProvider(auctionInvocationContextProvider(endpoint, auctionContext))
                .withRejectAllowed(true)
                .execute();
    }

    public Future<HookStageExecutionResult<BidderRequestPayload>> executeBidderRequestStage(
            BidderRequest bidderRequest, AuctionContext auctionContext) {

        final Account account = auctionContext.getAccount();
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final String bidder = bidderRequest.getBidder();

        final Endpoint endpoint = context.getEndpoint();
        final Stage stage = Stage.bidder_request;

        return this
                .<BidderRequestPayload, BidderInvocationContext>stageExecutor(
                        stage, bidder, context, account, endpoint)
                .withInitialPayload(BidderRequestPayloadImpl.of(bidderRequest.getBidRequest()))
                .withHookProvider(hookId ->
                        hookCatalog.bidderRequestHookBy(hookId.getModuleCode(), hookId.getHookImplCode()))
                .withInvocationContextProvider(bidderInvocationContextProvider(endpoint, auctionContext, bidder))
                .withRejectAllowed(true)
                .execute();
    }

    public Future<HookStageExecutionResult<BidderResponsePayload>> executeRawBidderResponseStage(
            BidderResponse bidderResponse,
            AuctionContext auctionContext) {

        final Account account = auctionContext.getAccount();
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final List<BidderBid> bids = bidderResponse.getSeatBid().getBids();
        final String bidder = bidderResponse.getBidder();

        final Endpoint endpoint = context.getEndpoint();
        final Stage stage = Stage.raw_bidder_response;

        return this
                .<BidderResponsePayload, BidderInvocationContext>stageExecutor(
                        stage, bidder, context, account, endpoint)
                .withInitialPayload(BidderResponsePayloadImpl.of(bids))
                .withHookProvider(hookId ->
                        hookCatalog.rawBidderResponseHookBy(hookId.getModuleCode(), hookId.getHookImplCode()))
                .withInvocationContextProvider(bidderInvocationContextProvider(endpoint, auctionContext, bidder))
                .withRejectAllowed(true)
                .execute();
    }

    public Future<HookStageExecutionResult<BidderResponsePayload>> executeProcessedBidderResponseStage(
            BidderResponse bidderResponse,
            AuctionContext auctionContext) {

        final Account account = auctionContext.getAccount();
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final List<BidderBid> bids = bidderResponse.getSeatBid().getBids();
        final String bidder = bidderResponse.getBidder();

        final Endpoint endpoint = context.getEndpoint();
        final Stage stage = Stage.processed_bidder_response;

        return this
                .<BidderResponsePayload, BidderInvocationContext>stageExecutor(
                        stage, bidder, context, account, endpoint)
                .withInitialPayload(BidderResponsePayloadImpl.of(bids))
                .withHookProvider(hookId ->
                        hookCatalog.processedBidderResponseHookBy(hookId.getModuleCode(), hookId.getHookImplCode()))
                .withInvocationContextProvider(bidderInvocationContextProvider(endpoint, auctionContext, bidder))
                .withRejectAllowed(true)
                .execute();
    }

    public Future<HookStageExecutionResult<AuctionResponsePayload>> executeAuctionResponseStage(
            BidResponse bidResponse,
            AuctionContext auctionContext) {

        final Account account = auctionContext.getAccount();
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final Endpoint endpoint = context.getEndpoint();
        final Stage stage = Stage.auction_response;

        return this.<AuctionResponsePayload, AuctionInvocationContext>stageExecutor(
                stage, ENTITY_AUCTION_RESPONSE, context, account, endpoint)
                .withInitialPayload(AuctionResponsePayloadImpl.of(bidResponse))
                .withHookProvider(hookId ->
                        hookCatalog.auctionResponseHookBy(hookId.getModuleCode(), hookId.getHookImplCode()))
                .withInvocationContextProvider(auctionInvocationContextProvider(endpoint, auctionContext))
                .withRejectAllowed(false)
                .execute();
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> StageExecutor<PAYLOAD, CONTEXT> stageExecutor(
            Stage stage, String entity, HookExecutionContext context) {

        return StageExecutor.<PAYLOAD, CONTEXT>create(vertx, clock)
                .withStage(stage)
                .withEntity(entity)
                .withHookExecutionContext(context);
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> StageExecutor<PAYLOAD, CONTEXT> stageExecutor(
            Stage stage, String entity, HookExecutionContext context, Account account, Endpoint endpoint) {

        return this.<PAYLOAD, CONTEXT>stageExecutor(stage, entity, context)
                .withExecutionPlan(planForStage(account, endpoint, stage));
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
        return effectiveStagePlanFrom(ExecutionPlan.empty(), endpoint, Stage.entrypoint);
    }

    private StageExecutionPlan planForStage(Account account, Endpoint endpoint, Stage stage) {
        return effectiveStagePlanFrom(effectiveExecutionPlanFor(account), endpoint, stage);
    }

    private StageExecutionPlan effectiveStagePlanFrom(
            ExecutionPlan accountExecutionPlan, Endpoint endpoint, Stage stage) {

        final StageExecutionPlan hostStageExecutionPlan = stagePlanFrom(hostExecutionPlan, endpoint, stage);
        final StageExecutionPlan accountStageExecutionPlan = stagePlanFrom(accountExecutionPlan, endpoint, stage);

        if (hostStageExecutionPlan.isEmpty()) {
            return accountStageExecutionPlan;
        } else if (accountStageExecutionPlan.isEmpty()) {
            return hostStageExecutionPlan;
        }

        final List<ExecutionGroup> combinedGroups = Stream.of(hostStageExecutionPlan, accountStageExecutionPlan)
                .map(StageExecutionPlan::getGroups)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return StageExecutionPlan.of(combinedGroups);
    }

    private static StageExecutionPlan stagePlanFrom(ExecutionPlan executionPlan, Endpoint endpoint, Stage stage) {
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

        return accountExecutionPlan != null ? accountExecutionPlan : defaultAccountExecutionPlan;
    }

    private InvocationContextProvider<InvocationContext> invocationContextProvider(Endpoint endpoint) {
        return (timeout, hookId, moduleContext) -> invocationContext(endpoint, timeout);
    }

    private InvocationContextProvider<AuctionInvocationContext> auctionInvocationContextProvider(
            Endpoint endpoint,
            AuctionContext auctionContext) {

        return (timeout, hookId, moduleContext) -> auctionInvocationContext(
                endpoint, timeout, auctionContext, hookId, moduleContext);
    }

    private InvocationContextProvider<BidderInvocationContext> bidderInvocationContextProvider(
            Endpoint endpoint,
            AuctionContext auctionContext,
            String bidder) {

        return (timeout, hookId, moduleContext) -> BidderInvocationContextImpl.of(
                auctionInvocationContext(endpoint, timeout, auctionContext, hookId, moduleContext),
                bidder);
    }

    private InvocationContextImpl invocationContext(Endpoint endpoint, Long timeout) {
        return InvocationContextImpl.of(createTimeout(timeout), endpoint);
    }

    private AuctionInvocationContextImpl auctionInvocationContext(Endpoint endpoint,
                                                                  Long timeout,
                                                                  AuctionContext auctionContext,
                                                                  HookId hookId,
                                                                  Object moduleContext) {

        return AuctionInvocationContextImpl.of(
                invocationContext(endpoint, timeout),
                auctionContext.getDebugContext().isDebugEnabled(),
                accountConfigFor(auctionContext.getAccount(), hookId),
                moduleContext);
    }

    private Timeout createTimeout(Long timeout) {
        return timeoutFactory.create(timeout);
    }

    private static ObjectNode accountConfigFor(Account account, HookId hookId) {
        final AccountHooksConfiguration accountHooksConfiguration = account.getHooks();
        final Map<String, ObjectNode> modulesConfiguration =
                accountHooksConfiguration != null ? accountHooksConfiguration.getModules() : Collections.emptyMap();

        return modulesConfiguration != null ? modulesConfiguration.get(hookId.getModuleCode()) : null;
    }
}
