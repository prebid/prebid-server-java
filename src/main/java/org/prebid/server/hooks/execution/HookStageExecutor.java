package org.prebid.server.hooks.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.map.DefaultedMap;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.model.ABTest;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.execution.model.StageWithHookType;
import org.prebid.server.hooks.execution.provider.HookProvider;
import org.prebid.server.hooks.execution.provider.abtest.ABTestHookProvider;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.execution.v1.exitpoint.ExitpointPayloadImpl;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.hooks.v1.exitpoint.ExitpointPayload;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountHooksConfiguration;
import org.prebid.server.settings.model.HooksAdminConfig;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class HookStageExecutor {

    private static final String ENTITY_HTTP_REQUEST = "http-request";
    private static final String ENTITY_HTTP_RESPONSE = "http-response";
    private static final String ENTITY_AUCTION_REQUEST = "auction-request";
    private static final String ENTITY_AUCTION_RESPONSE = "auction-response";
    private static final String ENTITY_ALL_PROCESSED_BID_RESPONSES = "all-processed-bid-responses";
    private static final Account EMPTY_ACCOUNT = Account.empty(StringUtils.EMPTY);

    private final ExecutionPlan hostExecutionPlan;
    private final ExecutionPlan defaultAccountExecutionPlan;
    private final Map<String, Boolean> hostModuleExecution;
    private final HookCatalog hookCatalog;
    private final TimeoutFactory timeoutFactory;
    private final Vertx vertx;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final boolean isConfigToInvokeRequired;

    private HookStageExecutor(ExecutionPlan hostExecutionPlan,
                              ExecutionPlan defaultAccountExecutionPlan,
                              Map<String, Boolean> hostModuleExecution,
                              HookCatalog hookCatalog,
                              TimeoutFactory timeoutFactory,
                              Vertx vertx,
                              Clock clock,
                              ObjectMapper mapper,
                              boolean isConfigToInvokeRequired) {

        this.hostExecutionPlan = hostExecutionPlan;
        this.defaultAccountExecutionPlan = defaultAccountExecutionPlan;
        this.hookCatalog = hookCatalog;
        this.timeoutFactory = timeoutFactory;
        this.vertx = vertx;
        this.clock = clock;
        this.mapper = mapper;
        this.isConfigToInvokeRequired = isConfigToInvokeRequired;
        this.hostModuleExecution = hostModuleExecution;
    }

    public static HookStageExecutor create(String hostExecutionPlan,
                                           String defaultAccountExecutionPlan,
                                           Map<String, Boolean> hostModuleExecution,
                                           HookCatalog hookCatalog,
                                           TimeoutFactory timeoutFactory,
                                           Vertx vertx,
                                           Clock clock,
                                           JacksonMapper mapper,
                                           boolean isConfigToInvokeRequired) {

        Objects.requireNonNull(hookCatalog);
        Objects.requireNonNull(mapper);

        return new HookStageExecutor(
                parseAndValidateExecutionPlan(hostExecutionPlan, mapper, hookCatalog),
                parseAndValidateExecutionPlan(defaultAccountExecutionPlan, mapper, hookCatalog),
                hostModuleExecution,
                hookCatalog,
                Objects.requireNonNull(timeoutFactory),
                Objects.requireNonNull(vertx),
                Objects.requireNonNull(clock),
                mapper.mapper(),
                isConfigToInvokeRequired);
    }

    private static ExecutionPlan parseAndValidateExecutionPlan(String executionPlan,
                                                               JacksonMapper mapper,
                                                               HookCatalog hookCatalog) {

        return validateExecutionPlan(parseExecutionPlan(executionPlan, mapper), hookCatalog);
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

    private static ExecutionPlan validateExecutionPlan(ExecutionPlan plan, HookCatalog hookCatalog) {
        plan.getEndpoints().values().stream()
                .map(EndpointExecutionPlan::getStages)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .forEach(stageToPlan -> stageToPlan.getValue().getGroups().stream()
                        .map(ExecutionGroup::getHookSequence)
                        .flatMap(Collection::stream)
                        .forEach(hookId -> validateHookId(stageToPlan.getKey(), hookId, hookCatalog)));

        return plan;
    }

    private static void validateHookId(Stage stage, HookId hookId, HookCatalog hookCatalog) {
        try {
            hookCatalog.hookById(hookId, StageWithHookType.forStage(stage));
        } catch (Throwable e) {
            throw new IllegalArgumentException(
                    "Hooks execution plan contains unknown or disabled hook: stage=%s, hookId=%s"
                            .formatted(stage, hookId));
        }
    }

    public Future<HookStageExecutionResult<EntrypointPayload>> executeEntrypointStage(
            CaseInsensitiveMultiMap queryParams,
            CaseInsensitiveMultiMap headers,
            String body,
            HookExecutionContext context) {

        final Endpoint endpoint = context.getEndpoint();

        return stageExecutor(StageWithHookType.ENTRYPOINT, ENTITY_HTTP_REQUEST, context)
                .withExecutionPlan(planForEntrypointStage(endpoint))
                .withHookProvider(hookProviderForEntrypointStage(context))
                .withInitialPayload(EntrypointPayloadImpl.of(queryParams, headers, body))
                .withInvocationContextProvider(invocationContextProvider(endpoint))
                .withModulesExecution(DefaultedMap.defaultedMap(hostModuleExecution, true))
                .withRejectAllowed(true)
                .execute();
    }

    public Future<HookStageExecutionResult<AuctionRequestPayload>> executeRawAuctionRequestStage(
            AuctionContext auctionContext) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final Endpoint endpoint = context.getEndpoint();

        return this
                .stageExecutor(
                        StageWithHookType.RAW_AUCTION_REQUEST, ENTITY_AUCTION_REQUEST, context, account, endpoint)
                .withInitialPayload(AuctionRequestPayloadImpl.of(bidRequest))
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

        return this
                .stageExecutor(
                        StageWithHookType.PROCESSED_AUCTION_REQUEST, ENTITY_AUCTION_REQUEST, context, account, endpoint)
                .withInitialPayload(AuctionRequestPayloadImpl.of(bidRequest))
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

        return this
                .stageExecutor(StageWithHookType.BIDDER_REQUEST, bidder, context, account, endpoint)
                .withInitialPayload(BidderRequestPayloadImpl.of(bidderRequest.getBidRequest()))
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

        return this
                .stageExecutor(StageWithHookType.RAW_BIDDER_RESPONSE, bidder, context, account, endpoint)
                .withInitialPayload(BidderResponsePayloadImpl.of(bids))
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

        return stageExecutor(StageWithHookType.PROCESSED_BIDDER_RESPONSE, bidder, context, account, endpoint)
                .withInitialPayload(BidderResponsePayloadImpl.of(bids))
                .withInvocationContextProvider(bidderInvocationContextProvider(endpoint, auctionContext, bidder))
                .withRejectAllowed(true)
                .execute();
    }

    public Future<HookStageExecutionResult<AllProcessedBidResponsesPayload>> executeAllProcessedBidResponsesStage(
            List<BidderResponse> bidderResponses,
            AuctionContext auctionContext) {

        final Account account = auctionContext.getAccount();
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final Endpoint endpoint = context.getEndpoint();

        return stageExecutor(
                StageWithHookType.ALL_PROCESSED_BID_RESPONSES, ENTITY_ALL_PROCESSED_BID_RESPONSES,
                context, account, endpoint)
                .withInitialPayload(AllProcessedBidResponsesPayloadImpl.of(bidderResponses))
                .withInvocationContextProvider(auctionInvocationContextProvider(endpoint, auctionContext))
                .withRejectAllowed(false)
                .execute();
    }

    public Future<HookStageExecutionResult<AuctionResponsePayload>> executeAuctionResponseStage(
            BidResponse bidResponse,
            AuctionContext auctionContext) {

        final Account account = ObjectUtils.defaultIfNull(auctionContext.getAccount(), EMPTY_ACCOUNT);
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final Endpoint endpoint = context.getEndpoint();

        return stageExecutor(StageWithHookType.AUCTION_RESPONSE, ENTITY_AUCTION_RESPONSE, context, account, endpoint)
                .withInitialPayload(AuctionResponsePayloadImpl.of(bidResponse))
                .withInvocationContextProvider(auctionInvocationContextProvider(endpoint, auctionContext))
                .withRejectAllowed(false)
                .execute();
    }

    public Future<HookStageExecutionResult<ExitpointPayload>> executeExitpointStage(MultiMap responseHeaders,
                                                                                    String responseBody,
                                                                                    AuctionContext auctionContext) {

        final Account account = ObjectUtils.defaultIfNull(auctionContext.getAccount(), EMPTY_ACCOUNT);
        final HookExecutionContext context = auctionContext.getHookExecutionContext();

        final Endpoint endpoint = context.getEndpoint();

        return stageExecutor(StageWithHookType.EXITPOINT, ENTITY_HTTP_RESPONSE, context, account, endpoint)
                .withInitialPayload(ExitpointPayloadImpl.of(responseHeaders, responseBody))
                .withInvocationContextProvider(auctionInvocationContextProvider(endpoint, auctionContext))
                .withRejectAllowed(false)
                .execute();
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> StageExecutor<PAYLOAD, CONTEXT> stageExecutor(
            StageWithHookType<? extends Hook<PAYLOAD, CONTEXT>> stage,
            String entity,
            HookExecutionContext context) {

        return StageExecutor.<PAYLOAD, CONTEXT>create(vertx, clock)
                .withStage(stage)
                .withEntity(entity)
                .withHookExecutionContext(context);
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> StageExecutor<PAYLOAD, CONTEXT> stageExecutor(
            StageWithHookType<? extends Hook<PAYLOAD, CONTEXT>> stage,
            String entity,
            HookExecutionContext context,
            Account account,
            Endpoint endpoint) {

        return stageExecutor(stage, entity, context)
                .withModulesExecution(modulesExecutionForAccount(account))
                .withExecutionPlan(planForStage(account, endpoint, stage.stage()))
                .withHookProvider(hookProvider(stage, account, context));
    }

    private Map<String, Boolean> modulesExecutionForAccount(Account account) {
        final Map<String, Boolean> accountModulesExecution = Optional.ofNullable(account.getHooks())
                .map(AccountHooksConfiguration::getAdmin)
                .map(HooksAdminConfig::getModuleExecution)
                .orElse(Collections.emptyMap());

        final Map<String, Boolean> resultModulesExecution = new HashMap<>(accountModulesExecution);

        if (isConfigToInvokeRequired) {
            Optional.ofNullable(account.getHooks())
                    .map(AccountHooksConfiguration::getModules)
                    .map(Map::keySet)
                    .stream()
                    .flatMap(Collection::stream)
                    .forEach(module -> resultModulesExecution.computeIfAbsent(module, key -> true));
        }

        resultModulesExecution.putAll(hostModuleExecution);
        return DefaultedMap.defaultedMap(resultModulesExecution, !isConfigToInvokeRequired);
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
                .toList();

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

    private HookProvider<EntrypointPayload, InvocationContext> hookProviderForEntrypointStage(
            HookExecutionContext context) {

        return new ABTestHookProvider<>(
                defaultHookProvider(StageWithHookType.ENTRYPOINT),
                abTestsForEntrypointStage(),
                context,
                mapper);
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> HookProvider<PAYLOAD, CONTEXT> hookProvider(
            StageWithHookType<? extends Hook<PAYLOAD, CONTEXT>> stage,
            Account account,
            HookExecutionContext context) {

        return new ABTestHookProvider<>(
                defaultHookProvider(stage),
                abTests(account),
                context,
                mapper);
    }

    private <PAYLOAD, CONTEXT extends InvocationContext> HookProvider<PAYLOAD, CONTEXT> defaultHookProvider(
            StageWithHookType<? extends Hook<PAYLOAD, CONTEXT>> stage) {

        return hookId -> hookCatalog.hookById(hookId, stage);
    }

    private InvocationContextProvider<InvocationContext> invocationContextProvider(Endpoint endpoint) {
        return (timeout, hookId, moduleContext) -> invocationContext(endpoint, timeout);
    }

    private InvocationContextImpl invocationContext(Endpoint endpoint, Long timeout) {
        return InvocationContextImpl.of(createTimeout(timeout), endpoint);
    }

    private InvocationContextProvider<AuctionInvocationContext> auctionInvocationContextProvider(
            Endpoint endpoint,
            AuctionContext auctionContext) {

        return (timeout, hookId, moduleContext) -> auctionInvocationContext(
                endpoint, timeout, auctionContext, hookId, moduleContext);
    }

    private AuctionInvocationContextImpl auctionInvocationContext(Endpoint endpoint,
                                                                  Long timeout,
                                                                  AuctionContext auctionContext,
                                                                  HookId hookId,
                                                                  Object moduleContext) {

        return AuctionInvocationContextImpl.of(
                invocationContext(endpoint, timeout),
                auctionContext,
                auctionContext.getDebugContext().isDebugEnabled(),
                accountConfigFor(auctionContext.getAccount(), hookId),
                moduleContext);
    }

    private InvocationContextProvider<BidderInvocationContext> bidderInvocationContextProvider(
            Endpoint endpoint,
            AuctionContext auctionContext,
            String bidder) {

        return (timeout, hookId, moduleContext) -> BidderInvocationContextImpl.of(
                auctionInvocationContext(endpoint, timeout, auctionContext, hookId, moduleContext),
                bidder);
    }

    private Timeout createTimeout(Long timeout) {
        return timeoutFactory.create(timeout);
    }

    private static ObjectNode accountConfigFor(Account account, HookId hookId) {
        final AccountHooksConfiguration accountHooksConfiguration = account != null ? account.getHooks() : null;
        final Map<String, ObjectNode> modulesConfiguration =
                accountHooksConfiguration != null ? accountHooksConfiguration.getModules() : Collections.emptyMap();

        return modulesConfiguration != null ? modulesConfiguration.get(hookId.getModuleCode()) : null;
    }

    protected List<ABTest> abTestsForEntrypointStage() {
        return ListUtils.emptyIfNull(hostExecutionPlan.getAbTests()).stream()
                .filter(HookStageExecutor::isABTestEnabled)
                .toList();
    }

    private static boolean isABTestEnabled(ABTest abTest) {
        return abTest != null && abTest.isEnabled();
    }

    protected List<ABTest> abTests(Account account) {
        return abTestsFromAccount(account)
                .or(() -> abTestsFromHostConfig(account.getId()))
                .orElse(Collections.emptyList());
    }

    private Optional<List<ABTest>> abTestsFromAccount(Account account) {
        return Optional.of(effectiveExecutionPlanFor(account))
                .map(ExecutionPlan::getAbTests)
                .map(abTests -> abTests.stream()
                        .filter(HookStageExecutor::isABTestEnabled)
                        .toList());
    }

    private Optional<List<ABTest>> abTestsFromHostConfig(String accountId) {
        return Optional.ofNullable(hostExecutionPlan.getAbTests())
                .map(abTests -> abTests.stream()
                        .filter(HookStageExecutor::isABTestEnabled)
                        .filter(abTest -> isABTestApplicable(abTest, accountId))
                        .toList());
    }

    private static boolean isABTestApplicable(ABTest abTest, String account) {
        final Set<String> accounts = abTest.getAccounts();
        return CollectionUtils.isEmpty(accounts) || accounts.contains(account);
    }
}
