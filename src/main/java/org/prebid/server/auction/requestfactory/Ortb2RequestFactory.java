package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.deals.DealsProcessor;
import org.prebid.server.deals.model.DeepDebugLog;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.PriceFloorFetcher;
import org.prebid.server.floors.PriceFloorResolver;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchResult;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Ortb2RequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(Ortb2RequestFactory.class);

    private static final ConditionalLogger EMPTY_ACCOUNT_LOGGER = new ConditionalLogger("empty_account", logger);
    private static final ConditionalLogger UNKNOWN_ACCOUNT_LOGGER = new ConditionalLogger("unknown_account", logger);

    private final boolean enforceValidAccount;
    private final boolean priceFloorsEnabled;
    private final List<String> blacklistedAccounts;
    private final UidsCookieService uidsCookieService;
    private final RequestValidator requestValidator;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ApplicationSettings applicationSettings;
    private final DealsProcessor dealsProcessor;
    private final IpAddressHelper ipAddressHelper;
    private final HookStageExecutor hookStageExecutor;
    private final CountryCodeMapper countryCodeMapper;
    private final PriceFloorFetcher floorFetcher;
    private final PriceFloorResolver floorResolver;
    private final JacksonMapper mapper;
    private final Clock clock;

    public Ortb2RequestFactory(boolean enforceValidAccount,
                               boolean priceFloorsEnabled,
                               List<String> blacklistedAccounts,
                               UidsCookieService uidsCookieService,
                               RequestValidator requestValidator,
                               TimeoutResolver timeoutResolver,
                               TimeoutFactory timeoutFactory,
                               StoredRequestProcessor storedRequestProcessor,
                               ApplicationSettings applicationSettings,
                               IpAddressHelper ipAddressHelper,
                               HookStageExecutor hookStageExecutor,
                               DealsProcessor dealsProcessor,
                               CountryCodeMapper countryCodeMapper,
                               PriceFloorFetcher floorFetcher,
                               PriceFloorResolver floorResolver,
                               JacksonMapper mapper,
                               Clock clock) {

        this.enforceValidAccount = enforceValidAccount;
        this.priceFloorsEnabled = priceFloorsEnabled;
        this.blacklistedAccounts = Objects.requireNonNull(blacklistedAccounts);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
        this.dealsProcessor = dealsProcessor;
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
        this.floorFetcher = Objects.requireNonNull(floorFetcher);
        this.floorResolver = Objects.requireNonNull(floorResolver);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public AuctionContext createAuctionContext(Endpoint endpoint, MetricName requestTypeMetric) {
        return AuctionContext.builder()
                .requestTypeMetric(requestTypeMetric)
                .prebidErrors(new ArrayList<>())
                .debugWarnings(new ArrayList<>())
                .hookExecutionContext(HookExecutionContext.of(endpoint))
                .debugContext(DebugContext.empty())
                .requestRejected(false)
                .txnLog(TxnLog.create())
                .debugHttpCalls(new HashMap<>())
                .build();
    }

    public AuctionContext enrichAuctionContext(AuctionContext auctionContext,
                                               HttpRequestContext httpRequest,
                                               BidRequest bidRequest,
                                               long startTime) {

        return auctionContext.toBuilder()
                .httpRequest(httpRequest)
                .uidsCookie(uidsCookieService.parseFromRequest(httpRequest))
                .bidRequest(bidRequest)
                .timeout(timeout(bidRequest, startTime))
                .deepDebugLog(createDeepDebugLog(bidRequest))
                .build();
    }

    public Future<Account> fetchAccountWithoutStoredRequestLookup(AuctionContext auctionContext) {
        return fetchAccount(auctionContext, false);
    }

    public Future<Account> fetchAccount(AuctionContext auctionContext) {
        return fetchAccount(auctionContext, true);
    }

    private Future<Account> fetchAccount(AuctionContext auctionContext, boolean isLookupStoredRequest) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Timeout timeout = auctionContext.getTimeout();
        final HttpRequestContext httpRequest = auctionContext.getHttpRequest();

        return findAccountIdFrom(bidRequest, isLookupStoredRequest)
                .map(this::validateIfAccountBlacklisted)
                .compose(accountId -> loadAccount(timeout, httpRequest, accountId));
    }

    public Future<BidRequest> validateRequest(BidRequest bidRequest, List<String> warnings) {
        final ValidationResult validationResult = requestValidator.validate(bidRequest);

        if (validationResult.hasWarnings()) {
            warnings.addAll(validationResult.getWarnings());
        }

        return validationResult.hasErrors()
                ? Future.failedFuture(new InvalidRequestException(validationResult.getErrors()))
                : Future.succeededFuture(bidRequest);
    }

    public BidRequest enrichBidRequestWithAccountAndPrivacyData(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final PrivacyContext privacyContext = auctionContext.getPrivacyContext();

        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequest enrichedRequestExt = enrichExtRequest(requestExt, account);

        final Device device = bidRequest.getDevice();
        final Device enrichedDevice = enrichDevice(device, privacyContext);

        if (enrichedRequestExt != null || enrichedDevice != null) {
            return bidRequest.toBuilder()
                    .ext(ObjectUtils.defaultIfNull(enrichedRequestExt, requestExt))
                    .device(ObjectUtils.defaultIfNull(enrichedDevice, device))
                    .build();
        }

        return bidRequest;
    }

    public Future<HttpRequestContext> executeEntrypointHooks(RoutingContext routingContext,
                                                             String body,
                                                             AuctionContext auctionContext) {

        return hookStageExecutor.executeEntrypointStage(
                        toCaseInsensitiveMultiMap(routingContext.queryParams()),
                        toCaseInsensitiveMultiMap(routingContext.request().headers()),
                        body,
                        auctionContext.getHookExecutionContext())
                .map(stageResult -> toHttpRequest(stageResult, routingContext, auctionContext));
    }

    public Future<BidRequest> executeRawAuctionRequestHooks(AuctionContext auctionContext) {
        return hookStageExecutor.executeRawAuctionRequestStage(auctionContext)
                .map(stageResult -> toBidRequest(stageResult, auctionContext));
    }

    public Future<BidRequest> executeProcessedAuctionRequestHooks(AuctionContext auctionContext) {
        return hookStageExecutor.executeProcessedAuctionRequestStage(auctionContext)
                .map(stageResult -> toBidRequest(stageResult, auctionContext));
    }

    public Future<AuctionContext> restoreResultFromRejection(Throwable throwable) {
        if (throwable instanceof RejectedRequestException) {
            final AuctionContext auctionContext = ((RejectedRequestException) throwable).getAuctionContext();

            return Future.succeededFuture(auctionContext.withRequestRejected());
        }

        return Future.failedFuture(throwable);
    }

    private static HttpRequestContext toHttpRequest(HookStageExecutionResult<EntrypointPayload> stageResult,
                                                    RoutingContext routingContext,
                                                    AuctionContext auctionContext) {

        if (stageResult.isShouldReject()) {
            throw new RejectedRequestException(auctionContext);
        }

        return HttpRequestContext.builder()
                .absoluteUri(routingContext.request().absoluteURI())
                .queryParams(stageResult.getPayload().queryParams())
                .headers(stageResult.getPayload().headers())
                .body(stageResult.getPayload().body())
                .scheme(routingContext.request().scheme())
                .remoteHost(routingContext.request().remoteAddress().host())
                .build();
    }

    private static BidRequest toBidRequest(HookStageExecutionResult<AuctionRequestPayload> stageResult,
                                           AuctionContext auctionContext) {

        if (stageResult.isShouldReject()) {
            throw new RejectedRequestException(auctionContext);
        }

        return stageResult.getPayload().bidRequest();
    }

    public Future<AuctionContext> populateDealsInfo(AuctionContext auctionContext) {
        return dealsProcessor != null
                ? dealsProcessor.populateDealsInfo(auctionContext)
                : Future.succeededFuture(auctionContext);
    }

    public AuctionContext enrichWithPriceFloors(AuctionContext auctionContext) {
        final Account account = auctionContext.getAccount();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final ExtRequestPrebidFloors extFloors = extractExtFloors(bidRequest);

        if (isPriceFloorsDisabled(account, extFloors)) {
            return auctionContext;
        }

        final PriceFloorRuleExtractResult ruleExtractResult = resolveRules(account, extFloors);
        final BidRequest updatedBidRequest = updateBidRequestWithFloors(bidRequest, ruleExtractResult);
        return auctionContext.with(updatedBidRequest);
    }

    private static ExtRequestPrebidFloors extractExtFloors(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getFloors);
    }

    private boolean isPriceFloorsDisabled(Account account, ExtRequestPrebidFloors extFloors) {
        return !priceFloorsEnabled
                || isPriceFloorsDisabledForAccount(account)
                || isPriceFloorsDisabledForRequest(extFloors);
    }

    private static boolean isPriceFloorsDisabledForAccount(Account account) {
        final AccountPriceFloorsConfig priceFloors = ObjectUtil.getIfNotNull(account.getAuction(),
                AccountAuctionConfig::getPriceFloors);

        return BooleanUtils.isFalse(ObjectUtil.getIfNotNull(priceFloors, AccountPriceFloorsConfig::getEnabled));
    }

    private static boolean isPriceFloorsDisabledForRequest(ExtRequestPrebidFloors extFloors) {
        return BooleanUtils.isFalse(ObjectUtil.getIfNotNull(extFloors, ExtRequestPrebidFloors::getEnabled));
    }

    private PriceFloorRuleExtractResult resolveRules(Account account, ExtRequestPrebidFloors extFloors) {
        final FetchResult fetchResult = floorFetcher.fetch(account);
        // TODO: In what cases of fetchStatus we should fallback to request rules?
        if (fetchResult != null) {
            return PriceFloorRuleExtractResult.providerRules(fetchResult.getRules(), fetchResult.getFetchStatus());
        }

        final PriceFloorRules requestFloorRules = ObjectUtil.getIfNotNull(extFloors, ExtRequestPrebidFloors::getRules);
        if (requestFloorRules != null) {
            return PriceFloorRuleExtractResult.requestRules(requestFloorRules);
        }

        return PriceFloorRuleExtractResult.noRules();
    }

    private BidRequest updateBidRequestWithFloors(BidRequest bidRequest,
                                                  PriceFloorRuleExtractResult ruleExtractResult) {

        final PriceFloorData priceFloorData =
                ObjectUtil.getIfNotNull(ruleExtractResult.getRules(), PriceFloorRules::getData);
        final List<PriceFloorModelGroup> modelGroups =
                ObjectUtil.getIfNotNull(priceFloorData, PriceFloorData::getModelGroups);
        final PriceFloorModelGroup modelGroup = CollectionUtils.isNotEmpty(modelGroups)
                ? selectFloorModelGroup(modelGroups)
                : null;

        final ExtRequestPrebidFloors extFloors = extractExtFloors(bidRequest);
        final ExtRequestPrebidFloors updatedExtFloors = updateExtFloors(extFloors, modelGroup, ruleExtractResult);
        final ExtRequestPrebid updatedExtPrebid = updateExtRequestWithFloors(bidRequest.getExt(), updatedExtFloors);

        return bidRequest.toBuilder()
                .imp(updateImpsWithFloors(bidRequest, modelGroup))
                .ext(ExtRequest.of(updatedExtPrebid))
                .build();
    }

    private static PriceFloorModelGroup selectFloorModelGroup(List<PriceFloorModelGroup> modelGroups) {
        final int overallModelWeight = modelGroups.stream()
                .mapToInt(Ortb2RequestFactory::resolveModelGroupWeight)
                .sum();

        Collections.shuffle(modelGroups);

        final List<PriceFloorModelGroup> groupsByWeight = modelGroups.stream()
                .sorted(Comparator.comparing(PriceFloorModelGroup::getModelWeight))
                .collect(Collectors.toList());

        int winWeight = ThreadLocalRandom.current().nextInt(overallModelWeight);
        for (PriceFloorModelGroup modelGroup : groupsByWeight) {
            winWeight -= modelGroup.getModelWeight();

            if (winWeight <= 0) {
                return modelGroup;
            }
        }

        return groupsByWeight.get(groupsByWeight.size() - 1);
    }

    private static int resolveModelGroupWeight(PriceFloorModelGroup modelGroup) {
        return ObjectUtils.defaultIfNull(modelGroup.getModelWeight(), 1);
    }

    private static ExtRequestPrebidFloors updateExtFloors(ExtRequestPrebidFloors extFloors,
                                                          PriceFloorModelGroup modelGroup,
                                                          PriceFloorRuleExtractResult ruleExtractResult) {

        final PriceFloorRules updatedRules = updatePriceFloorRules(ruleExtractResult.getRules(), modelGroup);

        final ExtRequestPrebidFloors.ExtRequestPrebidFloorsBuilder extFloorsBuilder = extFloors != null
                ? extFloors.toBuilder()
                : ExtRequestPrebidFloors.builder();

        return extFloorsBuilder
                .fetchStatus(ruleExtractResult.getFetchStatus())
                .location(ruleExtractResult.getLocation())
                .rules(updatedRules)
                .build();
    }

    private static PriceFloorRules updatePriceFloorRules(PriceFloorRules priceFloorRules,
                                                         PriceFloorModelGroup modelGroup) {

        if (modelGroup == null) {
            return priceFloorRules;
        }

        final PriceFloorData priceFloorData = ObjectUtil.getIfNotNull(priceFloorRules, PriceFloorRules::getData);
        if (priceFloorData == null) {
            return priceFloorRules;
        }

        return priceFloorRules.toBuilder()
                .data(priceFloorData.toBuilder()
                        .modelGroups(Collections.singletonList(modelGroup))
                        .build())
                .build();
    }

    private ExtRequestPrebid updateExtRequestWithFloors(ExtRequest extRequest,
                                                        ExtRequestPrebidFloors updatedExtFloors) {

        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);

        final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = prebid != null
                ? prebid.toBuilder()
                : ExtRequestPrebid.builder();

        return prebidBuilder.floors(updatedExtFloors).build();
    }

    private List<Imp> updateImpsWithFloors(BidRequest bidRequest,
                                           PriceFloorModelGroup modelGroup) {

        return CollectionUtils.emptyIfNull(bidRequest.getImp()).stream()
                .map(imp -> updateImpWithFloors(imp, bidRequest, modelGroup))
                .collect(Collectors.toList());
    }

    private Imp updateImpWithFloors(Imp imp, BidRequest bidRequest, PriceFloorModelGroup modelGroup) {
        final String impCurrency = imp.getBidfloorcur();
        final List<String> requestCur = bidRequest.getCur();
        final String currency = StringUtils.isNotBlank(impCurrency)
                ? impCurrency
                : CollectionUtils.isNotEmpty(requestCur)
                ? requestCur.get(0) : null;

        final PriceFloorResult priceFloorResult = floorResolver.resolve(bidRequest, modelGroup, imp, currency);
        if (priceFloorResult == null) {
            return imp;
        }

        return imp.toBuilder()
                .bidfloor(priceFloorResult.getFloorValue())
                .bidfloorcur(priceFloorResult.getCurrency())
                .ext(updateImpExtWithFloors(imp.getExt(), priceFloorResult))
                .build();
    }

    private ObjectNode updateImpExtWithFloors(ObjectNode ext, PriceFloorResult priceFloorResult) {
        final JsonNode extPrebid = ext.path("prebid");
        final ObjectNode extPrebidAsObject = extPrebid.isObject()
                ? (ObjectNode) extPrebid
                : mapper.mapper().createObjectNode();

        final ExtImpPrebidFloors prebidFloors = ExtImpPrebidFloors.of(priceFloorResult.getFloorRule(),
                priceFloorResult.getFloorRuleValue(), priceFloorResult.getFloorValue());
        final ObjectNode floorsNode = mapper.mapper().valueToTree(prebidFloors);

        return floorsNode.isEmpty() ? ext : ext.set("prebid", extPrebidAsObject.set("floors", floorsNode));
    }

    /**
     * Returns {@link Timeout} based on request.tmax and adjustment value of {@link TimeoutResolver}.
     */
    private Timeout timeout(BidRequest bidRequest, long startTime) {
        final long resolvedRequestTimeout = timeoutResolver.resolve(bidRequest.getTmax());
        final long timeout = timeoutResolver.adjustTimeout(resolvedRequestTimeout);
        return timeoutFactory.create(startTime, timeout);
    }

    private Future<String> findAccountIdFrom(BidRequest bidRequest, boolean isLookupStoredRequest) {
        final String accountId = accountIdFrom(bidRequest);
        return StringUtils.isNotBlank(accountId) || !isLookupStoredRequest
                ? Future.succeededFuture(accountId)
                : storedRequestProcessor.processStoredRequests(accountId, bidRequest)
                .map(this::accountIdFrom);
    }

    private String validateIfAccountBlacklisted(String accountId) {
        if (CollectionUtils.isNotEmpty(blacklistedAccounts)
                && StringUtils.isNotBlank(accountId)
                && blacklistedAccounts.contains(accountId)) {

            throw new BlacklistedAccountException(
                    String.format("Prebid-server has blacklisted Account ID: %s, please "
                            + "reach out to the prebid server host.", accountId));
        }
        return accountId;
    }

    private Future<Account> loadAccount(Timeout timeout,
                                        HttpRequestContext httpRequest,
                                        String accountId) {
        return StringUtils.isBlank(accountId)
                ? responseForEmptyAccount(httpRequest)
                : applicationSettings.getAccountById(accountId, timeout)
                .compose(this::ensureAccountActive,
                        exception -> accountFallback(exception, accountId, httpRequest));
    }

    /**
     * Extracts publisher id either from {@link BidRequest}.app.publisher or {@link BidRequest}.site.publisher.
     * If neither is present returns empty string.
     */
    private String accountIdFrom(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        final Publisher appPublisher = app != null ? app.getPublisher() : null;
        final Site site = bidRequest.getSite();
        final Publisher sitePublisher = site != null ? site.getPublisher() : null;

        final Publisher publisher = ObjectUtils.defaultIfNull(appPublisher, sitePublisher);
        final String publisherId = publisher != null ? resolvePublisherId(publisher) : null;
        return ObjectUtils.defaultIfNull(publisherId, StringUtils.EMPTY);
    }

    /**
     * Resolves what value should be used as a publisher id - either taken from publisher.ext.parentAccount
     * or publisher.id in this respective priority.
     */
    private String resolvePublisherId(Publisher publisher) {
        final String parentAccountId = parentAccountIdFromExtPublisher(publisher.getExt());
        return ObjectUtils.defaultIfNull(parentAccountId, publisher.getId());
    }

    /**
     * Parses publisher.ext and returns parentAccount value from it. Returns null if any parsing error occurs.
     */
    private String parentAccountIdFromExtPublisher(ExtPublisher extPublisher) {
        final ExtPublisherPrebid extPublisherPrebid = extPublisher != null ? extPublisher.getPrebid() : null;
        return extPublisherPrebid != null ? StringUtils.stripToNull(extPublisherPrebid.getParentAccount()) : null;
    }

    private Future<Account> responseForEmptyAccount(HttpRequestContext httpRequest) {
        EMPTY_ACCOUNT_LOGGER.warn(accountErrorMessage("Account not specified", httpRequest), 100);
        return responseForUnknownAccount(StringUtils.EMPTY);
    }

    private static String accountErrorMessage(String message, HttpRequestContext httpRequest) {
        return String.format(
                "%s, Url: %s and Referer: %s",
                message,
                httpRequest.getAbsoluteUri(),
                httpRequest.getHeaders().get(HttpUtil.REFERER_HEADER));
    }

    private Future<Account> accountFallback(Throwable exception,
                                            String accountId,
                                            HttpRequestContext httpRequest) {

        if (exception instanceof PreBidException) {
            UNKNOWN_ACCOUNT_LOGGER.warn(accountErrorMessage(exception.getMessage(), httpRequest), 100);
        } else {
            logger.warn("Error occurred while fetching account: {0}", exception.getMessage());
            logger.debug("Error occurred while fetching account", exception);
        }

        // hide all errors occurred while fetching account
        return responseForUnknownAccount(accountId);
    }

    private Future<Account> responseForUnknownAccount(String accountId) {
        return enforceValidAccount
                ? Future.failedFuture(new UnauthorizedAccountException(
                String.format("Unauthorized account id: %s", accountId), accountId))
                : Future.succeededFuture(Account.empty(accountId));
    }

    private Future<Account> ensureAccountActive(Account account) {
        final String accountId = account.getId();

        return account.getStatus() == AccountStatus.inactive
                ? Future.failedFuture(new UnauthorizedAccountException(
                String.format("Account %s is inactive", accountId), accountId))
                : Future.succeededFuture(account);
    }

    private ExtRequest enrichExtRequest(ExtRequest ext, Account account) {
        final ExtRequestPrebid prebidExt = ObjectUtil.getIfNotNull(ext, ExtRequest::getPrebid);
        final String integration = ObjectUtil.getIfNotNull(prebidExt, ExtRequestPrebid::getIntegration);
        final String accountDefaultIntegration = accountDefaultIntegration(account);

        if (StringUtils.isBlank(integration) && StringUtils.isNotBlank(accountDefaultIntegration)) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidExtBuilder =
                    prebidExt != null ? prebidExt.toBuilder() : ExtRequestPrebid.builder();

            prebidExtBuilder.integration(accountDefaultIntegration);

            final ExtRequest updatedExt = ExtRequest.of(prebidExtBuilder.build());
            if (ext != null) {
                updatedExt.addProperties(ext.getProperties());
            }

            return updatedExt;
        }

        return null;
    }

    private Device enrichDevice(Device device, PrivacyContext privacyContext) {
        final String ipAddress = privacyContext.getIpAddress();
        final IpAddress ip = ipAddressHelper.toIpAddress(ipAddress);

        final String ipV4InRequest = ObjectUtil.getIfNotNull(device, Device::getIp);
        final String ipV4 = ip != null && ip.getVersion() == IpAddress.IP.v4 ? ipAddress : null;
        final boolean shouldUpdateIpV4 = ipV4 != null && !Objects.equals(ipV4InRequest, ipV4);

        final String ipV6InRequest = ObjectUtil.getIfNotNull(device, Device::getIpv6);
        final String ipV6 = ip != null && ip.getVersion() == IpAddress.IP.v6 ? ipAddress : null;
        final boolean shouldUpdateIpV6 = ipV6 != null && !Objects.equals(ipV6InRequest, ipV6);

        final Geo geo = ObjectUtil.getIfNotNull(device, Device::getGeo);
        final String countryInRequest = ObjectUtil.getIfNotNull(geo, Geo::getCountry);
        final String alpha3CountryCode = resolveAlpha3CountryCode(privacyContext);
        final boolean shouldUpdateCountry = alpha3CountryCode != null && !alpha3CountryCode.equals(countryInRequest);

        if (shouldUpdateIpV4 || shouldUpdateIpV6 || shouldUpdateCountry) {
            final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

            if (shouldUpdateIpV4) {
                deviceBuilder.ip(ipV4);
            }

            if (shouldUpdateIpV6) {
                deviceBuilder.ipv6(ipV6);
            }

            if (shouldUpdateCountry) {
                final Geo.GeoBuilder geoBuilder = geo != null ? geo.toBuilder() : Geo.builder();
                geoBuilder.country(alpha3CountryCode);
                deviceBuilder.geo(geoBuilder.build());
            }

            return deviceBuilder.build();
        }

        return null;
    }

    private String resolveAlpha3CountryCode(PrivacyContext privacyContext) {
        final String alpha2CountryCode = ObjectUtil.getIfNotNull(
                privacyContext.getTcfContext().getGeoInfo(), GeoInfo::getCountry);

        return countryCodeMapper.mapToAlpha3(alpha2CountryCode);
    }

    private static String accountDefaultIntegration(Account account) {
        final AccountAuctionConfig accountAuctionConfig = account.getAuction();

        return accountAuctionConfig != null ? accountAuctionConfig.getDefaultIntegration() : null;
    }

    private static CaseInsensitiveMultiMap toCaseInsensitiveMultiMap(MultiMap originalMap) {
        final CaseInsensitiveMultiMap.Builder mapBuilder = CaseInsensitiveMultiMap.builder();
        originalMap.entries().forEach(entry -> mapBuilder.add(entry.getKey(), entry.getValue()));

        return mapBuilder.build();
    }

    private DeepDebugLog createDeepDebugLog(BidRequest bidRequest) {
        final ExtRequest ext = bidRequest.getExt();
        return DeepDebugLog.create(ext != null && isDeepDebugEnabled(ext), clock);
    }

    /**
     * Determines deep debug flag from {@link ExtRequest}.
     */
    private static boolean isDeepDebugEnabled(ExtRequest extRequest) {
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        return extRequestPrebid != null && extRequestPrebid.getTrace() == TraceLevel.verbose;
    }

    static class RejectedRequestException extends RuntimeException {

        private final AuctionContext auctionContext;

        RejectedRequestException(AuctionContext auctionContext) {
            this.auctionContext = auctionContext;
        }

        public AuctionContext getAuctionContext() {
            return auctionContext;
        }
    }

    @Value(staticConstructor = "of")
    private static class PriceFloorRuleExtractResult {

        PriceFloorRules rules;

        FetchStatus fetchStatus;

        PriceFloorLocation location;

        public static PriceFloorRuleExtractResult noRules() {
            return PriceFloorRuleExtractResult.of(null, null, PriceFloorLocation.none);
        }

        public static PriceFloorRuleExtractResult requestRules(PriceFloorRules rules) {
            return PriceFloorRuleExtractResult.of(rules, null, PriceFloorLocation.request);
        }

        public static PriceFloorRuleExtractResult providerRules(PriceFloorRules rules, FetchStatus fetchStatus) {
            return PriceFloorRuleExtractResult.of(rules, fetchStatus, PriceFloorLocation.provider);
        }
    }
}
