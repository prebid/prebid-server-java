package org.prebid.server.auction.requestfactory;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.deals.UserAdditionalInfoService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.deals.model.DeepDebugLog;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.PriceFloorProcessor;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class Ortb2RequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(Ortb2RequestFactory.class);

    private static final ConditionalLogger EMPTY_ACCOUNT_LOGGER = new ConditionalLogger("empty_account", logger);
    private static final ConditionalLogger UNKNOWN_ACCOUNT_LOGGER = new ConditionalLogger("unknown_account", logger);

    private final boolean enforceValidAccount;
    private final double logSamplingRate;
    private final List<String> blacklistedAccounts;
    private final UidsCookieService uidsCookieService;
    private final RequestValidator requestValidator;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ApplicationSettings applicationSettings;
    private final UserAdditionalInfoService userAdditionalInfoService;
    private final IpAddressHelper ipAddressHelper;
    private final HookStageExecutor hookStageExecutor;
    private final PriceFloorProcessor priceFloorProcessor;
    private final CountryCodeMapper countryCodeMapper;
    private final Metrics metrics;
    private final Clock clock;

    public Ortb2RequestFactory(boolean enforceValidAccount,
                               double logSamplingRate,
                               List<String> blacklistedAccounts,
                               UidsCookieService uidsCookieService,
                               RequestValidator requestValidator,
                               TimeoutResolver timeoutResolver,
                               TimeoutFactory timeoutFactory,
                               StoredRequestProcessor storedRequestProcessor,
                               ApplicationSettings applicationSettings,
                               IpAddressHelper ipAddressHelper,
                               HookStageExecutor hookStageExecutor,
                               UserAdditionalInfoService userAdditionalInfoService,
                               PriceFloorProcessor priceFloorProcessor,
                               CountryCodeMapper countryCodeMapper,
                               Metrics metrics,
                               Clock clock) {

        this.enforceValidAccount = enforceValidAccount;
        this.logSamplingRate = logSamplingRate;
        this.blacklistedAccounts = Objects.requireNonNull(blacklistedAccounts);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
        this.userAdditionalInfoService = userAdditionalInfoService;
        this.priceFloorProcessor = Objects.requireNonNull(priceFloorProcessor);
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
        this.metrics = Objects.requireNonNull(metrics);
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
                .startTime(startTime)
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

    public Future<AuctionContext> populateUserAdditionalInfo(AuctionContext auctionContext) {
        return userAdditionalInfoService != null
                ? userAdditionalInfoService.populate(auctionContext)
                : Future.succeededFuture(auctionContext);
    }

    public AuctionContext enrichWithPriceFloors(AuctionContext auctionContext) {
        return priceFloorProcessor.enrichWithPriceFloors(auctionContext);
    }

    public AuctionContext updateTimeout(AuctionContext auctionContext, long startTime) {
        final Timeout currentTimeout = auctionContext.getTimeout();

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final BidRequest resolvedBidRequest = resolveBidRequest(bidRequest);
        final BidRequest effectiveBidRequest = resolvedBidRequest != null
                ? resolvedBidRequest
                : bidRequest;

        final Timeout requestTimeout = timeoutFactory.create(startTime, effectiveBidRequest.getTmax());
        if (requestTimeout.getDeadline() == currentTimeout.getDeadline()) {
            return resolvedBidRequest != null
                    ? auctionContext.with(resolvedBidRequest)
                    : auctionContext;
        }

        return auctionContext.toBuilder()
                .bidRequest(effectiveBidRequest)
                .timeout(requestTimeout)
                .build();
    }

    private BidRequest resolveBidRequest(BidRequest bidRequest) {
        final Long resolvedTmax = resolveTmax(bidRequest.getTmax());
        return resolvedTmax != null ? bidRequest.toBuilder().tmax(resolvedTmax).build() : null;
    }

    private Long resolveTmax(Long requestTimeout) {
        final long timeout = timeoutResolver.limitToMax(requestTimeout);
        return !Objects.equals(requestTimeout, timeout) ? timeout : null;
    }

    /**
     * Returns {@link Timeout} based on request.tmax and adjustment value of {@link TimeoutResolver}.
     */
    private Timeout timeout(BidRequest bidRequest, long startTime) {
        final long timeout = timeoutResolver.limitToMax(bidRequest.getTmax());
        return timeoutFactory.create(startTime, timeout);
    }

    private Future<String> findAccountIdFrom(BidRequest bidRequest, boolean isLookupStoredRequest) {
        final String accountId = accountIdFrom(bidRequest);
        return StringUtils.isNotBlank(accountId) || !isLookupStoredRequest
                ? Future.succeededFuture(accountId)
                : storedRequestProcessor.processAuctionRequest(accountId, bidRequest)
                .map(storedAuctionResult -> accountIdFrom(storedAuctionResult.bidRequest()));
    }

    private String validateIfAccountBlacklisted(String accountId) {
        if (CollectionUtils.isNotEmpty(blacklistedAccounts)
                && StringUtils.isNotBlank(accountId)
                && blacklistedAccounts.contains(accountId)) {

            throw new BlacklistedAccountException(
                    "Prebid-server has blacklisted Account ID: %s, please reach out to the prebid server host."
                            .formatted(accountId));
        }
        return accountId;
    }

    private Future<Account> loadAccount(Timeout timeout,
                                        HttpRequestContext httpRequest,
                                        String accountId) {

        final Future<Account> accountFuture = StringUtils.isBlank(accountId)
                ? responseForEmptyAccount(httpRequest)
                : applicationSettings.getAccountById(accountId, timeout)
                .compose(this::ensureAccountActive,
                        exception -> accountFallback(exception, accountId, httpRequest));

        return accountFuture
                .onFailure(ignored -> metrics.updateAccountRequestRejectedByInvalidAccountMetrics(accountId));
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
        EMPTY_ACCOUNT_LOGGER.warn(accountErrorMessage("Account not specified", httpRequest), logSamplingRate);
        return responseForUnknownAccount(StringUtils.EMPTY);
    }

    private static String accountErrorMessage(String message, HttpRequestContext httpRequest) {
        return "%s, Url: %s and Referer: %s".formatted(
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
                "Unauthorized account id: " + accountId, accountId))
                : Future.succeededFuture(Account.empty(accountId));
    }

    private Future<Account> ensureAccountActive(Account account) {
        final String accountId = account.getId();

        return account.getStatus() == AccountStatus.inactive
                ? Future.failedFuture(new UnauthorizedAccountException(
                "Account %s is inactive".formatted(accountId), accountId))
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
}
