package org.prebid.server.auction.requestfactory;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.cookie.UidsCookieService;
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
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsaTransparency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountDsaConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.settings.model.AccountTargetingConfig;
import org.prebid.server.settings.model.DefaultDsa;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

public class Ortb2RequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(Ortb2RequestFactory.class);

    private static final ConditionalLogger EMPTY_ACCOUNT_LOGGER = new ConditionalLogger("empty_account", logger);
    private static final ConditionalLogger UNKNOWN_ACCOUNT_LOGGER = new ConditionalLogger("unknown_account", logger);

    private final int timeoutAdjustmentFactor;
    private final double logSamplingRate;
    private final List<String> blacklistedAccounts;
    private final UidsCookieService uidsCookieService;
    private final ActivityInfrastructureCreator activityInfrastructureCreator;
    private final RequestValidator requestValidator;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ApplicationSettings applicationSettings;
    private final IpAddressHelper ipAddressHelper;
    private final HookStageExecutor hookStageExecutor;
    private final PriceFloorProcessor priceFloorProcessor;
    private final CountryCodeMapper countryCodeMapper;
    private final Metrics metrics;

    public Ortb2RequestFactory(int timeoutAdjustmentFactor,
                               double logSamplingRate,
                               List<String> blacklistedAccounts,
                               UidsCookieService uidsCookieService,
                               ActivityInfrastructureCreator activityInfrastructureCreator,
                               RequestValidator requestValidator,
                               TimeoutResolver timeoutResolver,
                               TimeoutFactory timeoutFactory,
                               StoredRequestProcessor storedRequestProcessor,
                               ApplicationSettings applicationSettings,
                               IpAddressHelper ipAddressHelper,
                               HookStageExecutor hookStageExecutor,
                               PriceFloorProcessor priceFloorProcessor,
                               CountryCodeMapper countryCodeMapper,
                               Metrics metrics) {

        if (timeoutAdjustmentFactor < 0 || timeoutAdjustmentFactor > 100) {
            throw new IllegalArgumentException("Expected timeout adjustment factor should be in [0, 100].");
        }

        this.timeoutAdjustmentFactor = timeoutAdjustmentFactor;
        this.logSamplingRate = logSamplingRate;
        this.blacklistedAccounts = Objects.requireNonNull(blacklistedAccounts);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.activityInfrastructureCreator = Objects.requireNonNull(activityInfrastructureCreator);
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
        this.priceFloorProcessor = Objects.requireNonNull(priceFloorProcessor);
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public AuctionContext createAuctionContext(Endpoint endpoint, MetricName requestTypeMetric) {
        return AuctionContext.builder()
                .requestTypeMetric(requestTypeMetric)
                .prebidErrors(new ArrayList<>())
                .debugWarnings(new ArrayList<>())
                .hookExecutionContext(HookExecutionContext.of(endpoint))
                .debugContext(DebugContext.empty())
                .requestRejected(false)
                .debugHttpCalls(new HashMap<>())
                .bidRejectionTrackers(new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
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
                .timeoutContext(TimeoutContext.of(startTime, timeout(bidRequest, startTime), timeoutAdjustmentFactor))
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
        final Timeout timeout = auctionContext.getTimeoutContext().getTimeout();
        final HttpRequestContext httpRequest = auctionContext.getHttpRequest();

        return findAccountIdFrom(bidRequest, isLookupStoredRequest)
                .map(this::validateIfAccountBlacklisted)
                .compose(accountId -> loadAccount(timeout, httpRequest, accountId));
    }

    public Future<ActivityInfrastructure> activityInfrastructureFrom(AuctionContext auctionContext) {
        return Future.succeededFuture(activityInfrastructureCreator.create(
                auctionContext.getAccount(),
                auctionContext.getGppContext(),
                auctionContext.getDebugContext().getTraceLevel()));
    }

    public Future<BidRequest> validateRequest(BidRequest bidRequest,
                                              HttpRequestContext httpRequestContext,
                                              List<String> warnings) {

        final ValidationResult validationResult = requestValidator.validate(bidRequest, httpRequestContext);

        if (validationResult.hasWarnings()) {
            warnings.addAll(validationResult.getWarnings());
        }

        return validationResult.hasErrors()
                ? Future.failedFuture(new InvalidRequestException(validationResult.getErrors()))
                : Future.succeededFuture(bidRequest);
    }

    public Future<BidRequest> enrichBidRequestWithGeolocationData(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Device device = bidRequest.getDevice();
        final GeoInfo geoInfo = auctionContext.getGeoInfo();
        final Geo geo = ObjectUtil.getIfNotNull(device, Device::getGeo);

        final UpdateResult<String> resolvedCountry = resolveCountry(geo, geoInfo);
        final UpdateResult<String> resolvedRegion = resolveRegion(geo, geoInfo);

        if (!resolvedCountry.isUpdated() && !resolvedRegion.isUpdated()) {
            return Future.succeededFuture(bidRequest);
        }

        final Geo updatedGeo = Optional.ofNullable(geo)
                .map(Geo::toBuilder)
                .orElseGet(Geo::builder)
                .country(resolvedCountry.getValue())
                .region(resolvedRegion.getValue())
                .build();

        final Device updatedDevice = Optional.ofNullable(device)
                .map(Device::toBuilder)
                .orElseGet(Device::builder)
                .geo(updatedGeo)
                .build();

        return Future.succeededFuture(bidRequest.toBuilder().device(updatedDevice).build());
    }

    public Future<BidRequest> enrichBidRequestWithAccountAndPrivacyData(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final PrivacyContext privacyContext = auctionContext.getPrivacyContext();

        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequest enrichedRequestExt = enrichExtRequest(requestExt, account);

        final Device device = bidRequest.getDevice();
        final Device enrichedDevice = enrichDevice(device, privacyContext);

        final Regs regs = bidRequest.getRegs();
        final Regs enrichedRegs = enrichRegs(regs, privacyContext, account);

        if (enrichedRequestExt == null && enrichedDevice == null && enrichedRegs == null) {
            return Future.succeededFuture(bidRequest);
        }

        return Future.succeededFuture(bidRequest.toBuilder()
                .ext(ObjectUtils.defaultIfNull(enrichedRequestExt, requestExt))
                .device(ObjectUtils.defaultIfNull(enrichedDevice, device))
                .regs(ObjectUtils.defaultIfNull(enrichedRegs, regs))
                .build());
    }

    private static Regs enrichRegs(Regs regs, PrivacyContext privacyContext, Account account) {
        final ExtRegs regsExt = regs != null ? regs.getExt() : null;
        final ExtRegsDsa regsExtDsa = regsExt != null ? regsExt.getDsa() : null;
        if (regsExtDsa != null) {
            return null;
        }

        final AccountDsaConfig accountDsaConfig = Optional.ofNullable(account)
                .map(Account::getPrivacy)
                .map(AccountPrivacyConfig::getDsa)
                .orElse(null);
        final DefaultDsa defaultDsa = accountDsaConfig != null ? accountDsaConfig.getDefaultDsa() : null;
        if (defaultDsa == null) {
            return null;
        }

        final boolean isGdprOnly = BooleanUtils.isTrue(accountDsaConfig.getGdprOnly());
        if (isGdprOnly && !privacyContext.getTcfContext().isInGdprScope()) {
            return null;
        }

        return Optional.ofNullable(regs)
                .map(Regs::toBuilder)
                .orElseGet(Regs::builder)
                .ext(mapRegsExtDsa(defaultDsa, regsExt))
                .build();
    }

    private static ExtRegs mapRegsExtDsa(DefaultDsa defaultDsa, ExtRegs regsExt) {
        final List<ExtRegsDsaTransparency> enrichedDsaTransparencies = defaultDsa.getTransparency()
                .stream()
                .map(dsaTransparency -> ExtRegsDsaTransparency.of(
                        dsaTransparency.getDomain(), dsaTransparency.getDsaParams()))
                .toList();

        final ExtRegsDsa enrichedRegsExtDsa = ExtRegsDsa.of(
                defaultDsa.getDsaRequired(),
                defaultDsa.getPubRender(),
                defaultDsa.getDataToPub(),
                enrichedDsaTransparencies);

        final boolean isRegsExtPresent = regsExt != null;
        return ExtRegs.of(
                isRegsExtPresent ? regsExt.getGdpr() : null,
                isRegsExtPresent ? regsExt.getUsPrivacy() : null,
                isRegsExtPresent ? regsExt.getGpc() : null,
                enrichedRegsExtDsa);
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

    public AuctionContext enrichWithPriceFloors(AuctionContext auctionContext) {
        return priceFloorProcessor.enrichWithPriceFloors(auctionContext);
    }

    public AuctionContext updateTimeout(AuctionContext auctionContext) {
        final TimeoutContext timeoutContext = auctionContext.getTimeoutContext();
        final long startTime = timeoutContext.getStartTime();
        final Timeout currentTimeout = timeoutContext.getTimeout();

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
                .timeoutContext(timeoutContext.with(requestTimeout))
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

    private Future<Account> loadAccount(Timeout timeout, HttpRequestContext httpRequest, String accountId) {
        if (StringUtils.isBlank(accountId)) {
            EMPTY_ACCOUNT_LOGGER.warn(accountErrorMessage("Account not specified", httpRequest), logSamplingRate);
        }

        return applicationSettings.getAccountById(accountId, timeout)
                .compose(this::ensureAccountActive)
                .recover(exception -> wrapFailure(exception, accountId, httpRequest))
                .onFailure(ignored -> metrics.updateAccountRequestRejectedByInvalidAccountMetrics(accountId));
    }

    private Future<Account> ensureAccountActive(Account account) {
        final String accountId = account.getId();

        return account.getStatus() == AccountStatus.inactive
                ? Future.failedFuture(
                        new UnauthorizedAccountException("Account %s is inactive".formatted(accountId), accountId))
                : Future.succeededFuture(account);
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
        final Dooh dooh = bidRequest.getDooh();
        final Publisher doohPublisher = dooh != null ? dooh.getPublisher() : null;

        final Publisher publisher = ObjectUtils.firstNonNull(appPublisher, doohPublisher, sitePublisher);
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

    private Future<Account> wrapFailure(Throwable exception, String accountId, HttpRequestContext httpRequest) {
        if (exception instanceof UnauthorizedAccountException) {
            return Future.failedFuture(exception);
        } else if (exception instanceof PreBidException) {
            UNKNOWN_ACCOUNT_LOGGER.warn(accountErrorMessage(exception.getMessage(), httpRequest), 100);
        } else {
            metrics.updateAccountRequestRejectedByFailedFetch(accountId);
            logger.warn("Error occurred while fetching account: {}", exception.getMessage());
            logger.debug("Error occurred while fetching account", exception);
        }

        return Future.failedFuture(
                new UnauthorizedAccountException("Unauthorized account id: " + accountId, accountId));
    }

    private static String accountErrorMessage(String message, HttpRequestContext httpRequest) {
        return "%s, Url: %s and Referer: %s".formatted(
                message,
                httpRequest.getAbsoluteUri(),
                httpRequest.getHeaders().get(HttpUtil.REFERER_HEADER));
    }

    private ExtRequest enrichExtRequest(ExtRequest ext, Account account) {
        final AccountAuctionConfig accountAuctionConfig = account.getAuction();
        if (accountAuctionConfig == null) {
            return null;
        }

        final ExtRequestPrebid extPrebid = ext != null ? ext.getPrebid() : null;
        final UpdateResult<String> integration = resolveIntegration(extPrebid, accountAuctionConfig);
        final UpdateResult<ExtRequestTargeting> targeting = resolveExtPrebidTargeting(extPrebid, accountAuctionConfig);

        if (!integration.isUpdated() && !targeting.isUpdated()) {
            return null;
        }

        final ExtRequestPrebid updatedExtPrebid = Optional.ofNullable(extPrebid)
                .map(ExtRequestPrebid::toBuilder)
                .orElseGet(ExtRequestPrebid::builder)
                .integration(integration.getValue())
                .targeting(targeting.getValue())
                .build();

        final ExtRequest updatedExt = ExtRequest.of(updatedExtPrebid);
        Optional.ofNullable(ext)
                .map(FlexibleExtension::getProperties)
                .ifPresent(updatedExt::addProperties);

        return updatedExt;
    }

    private static UpdateResult<String> resolveIntegration(ExtRequestPrebid extPrebid,
                                                           AccountAuctionConfig accountAuctionConfig) {

        final String integration = extPrebid != null ? extPrebid.getIntegration() : null;
        if (StringUtils.isNotBlank(integration)) {
            return UpdateResult.unaltered(integration);
        }

        final String accountIntegration = accountAuctionConfig.getDefaultIntegration();
        return StringUtils.isNotBlank(accountIntegration)
                ? UpdateResult.updated(accountIntegration)
                : UpdateResult.unaltered(integration);
    }

    private static UpdateResult<ExtRequestTargeting> resolveExtPrebidTargeting(
            ExtRequestPrebid extPrebid,
            AccountAuctionConfig accountAuctionConfig) {

        final ExtRequestTargeting targeting = extPrebid != null ? extPrebid.getTargeting() : null;

        final AccountTargetingConfig accountTargeting = accountAuctionConfig.getTargeting();
        if (accountTargeting == null) {
            return UpdateResult.unaltered(targeting);
        }

        final TargetingValueResolver targetingValueResolver = new TargetingValueResolver(targeting, accountTargeting);

        final UpdateResult<Boolean> includeWinners = targetingValueResolver.resolveIncludeWinners();
        final UpdateResult<Boolean> includeBidderKeys = targetingValueResolver.resolveIncludeBidderKeys();
        final UpdateResult<Boolean> includeFormat = targetingValueResolver.resolveIncludeFormat();
        final UpdateResult<Boolean> preferDeals = targetingValueResolver.resolvePreferDeals();
        final UpdateResult<Boolean> alwaysIncludeDeals = targetingValueResolver.resolveAlwaysIncludeDeals();

        return includeWinners.isUpdated()
                || includeBidderKeys.isUpdated()
                || includeFormat.isUpdated()
                || preferDeals.isUpdated()
                || alwaysIncludeDeals.isUpdated()

                ? UpdateResult.updated(
                Optional.ofNullable(targeting)
                        .map(ExtRequestTargeting::toBuilder)
                        .orElseGet(ExtRequestTargeting::builder)
                        .includewinners(includeWinners.getValue())
                        .includebidderkeys(includeBidderKeys.getValue())
                        .includeformat(includeFormat.getValue())
                        .preferdeals(preferDeals.getValue())
                        .alwaysincludedeals(alwaysIncludeDeals.getValue())
                        .build())

                : UpdateResult.unaltered(targeting);
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
        final GeoInfo geoInfo = privacyContext.getTcfContext().getGeoInfo();

        final UpdateResult<String> resolvedCountry = resolveCountry(geo, geoInfo);
        final UpdateResult<String> resolvedRegion = resolveRegion(geo, geoInfo);

        if (shouldUpdateIpV4 || shouldUpdateIpV6 || resolvedCountry.isUpdated() || resolvedRegion.isUpdated()) {
            final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

            if (shouldUpdateIpV4) {
                deviceBuilder.ip(ipV4);
            }

            if (shouldUpdateIpV6) {
                deviceBuilder.ipv6(ipV6);
            }

            if (resolvedCountry.isUpdated() || resolvedRegion.isUpdated()) {
                final Geo updatedGeo = Optional.ofNullable(geo)
                        .map(Geo::toBuilder)
                        .orElseGet(Geo::builder)
                        .country(resolvedCountry.getValue())
                        .region(resolvedRegion.getValue())
                        .build();

                deviceBuilder.geo(updatedGeo);
            }

            return deviceBuilder.build();
        }

        return null;
    }

    private UpdateResult<String> resolveCountry(Geo originalGeo, GeoInfo geoInfo) {
        final String countryInRequest = originalGeo != null ? originalGeo.getCountry() : null;

        final String alpha2CountryCode = geoInfo != null ? geoInfo.getCountry() : null;
        final String alpha3CountryCode = countryCodeMapper.mapToAlpha3(alpha2CountryCode);

        return alpha3CountryCode != null && !alpha3CountryCode.equals(countryInRequest)
                ? UpdateResult.updated(alpha3CountryCode)
                : UpdateResult.unaltered(countryInRequest);
    }

    private static UpdateResult<String> resolveRegion(Geo originalGeo, GeoInfo geoInfo) {
        final String regionInRequest = originalGeo != null ? originalGeo.getRegion() : null;
        final String upperCasedRegionInRequest = StringUtils.upperCase(regionInRequest);

        final String region = geoInfo != null ? geoInfo.getRegion() : null;
        final String upperCasedRegion = StringUtils.upperCase(region);

        return upperCasedRegion != null && !upperCasedRegion.equals(upperCasedRegionInRequest)
                ? UpdateResult.updated(upperCasedRegion)
                : Objects.equals(regionInRequest, upperCasedRegionInRequest)
                ? UpdateResult.unaltered(regionInRequest)
                : UpdateResult.updated(upperCasedRegionInRequest);
    }

    private static CaseInsensitiveMultiMap toCaseInsensitiveMultiMap(MultiMap originalMap) {
        final CaseInsensitiveMultiMap.Builder mapBuilder = CaseInsensitiveMultiMap.builder();
        originalMap.entries().forEach(entry -> mapBuilder.add(entry.getKey(), entry.getValue()));

        return mapBuilder.build();
    }

    @Getter
    static class RejectedRequestException extends RuntimeException {

        private final AuctionContext auctionContext;

        RejectedRequestException(AuctionContext auctionContext) {
            this.auctionContext = auctionContext;
        }
    }

    private record TargetingValueResolver(ExtRequestTargeting targeting,
                                          AccountTargetingConfig accountTargetingConfig) {

        public UpdateResult<Boolean> resolveIncludeWinners() {
            return resolveValue(
                    ExtRequestTargeting::getIncludewinners,
                    AccountTargetingConfig::getIncludeWinners);
        }

        public UpdateResult<Boolean> resolveIncludeBidderKeys() {
            return resolveValue(
                    ExtRequestTargeting::getIncludebidderkeys,
                    AccountTargetingConfig::getIncludeBidderKeys);
        }

        public UpdateResult<Boolean> resolveIncludeFormat() {
            return resolveValue(
                    ExtRequestTargeting::getIncludeformat,
                    AccountTargetingConfig::getIncludeFormat);
        }

        public UpdateResult<Boolean> resolvePreferDeals() {
            return resolveValue(
                    ExtRequestTargeting::getPreferdeals,
                    AccountTargetingConfig::getPreferDeals);
        }

        public UpdateResult<Boolean> resolveAlwaysIncludeDeals() {
            return resolveValue(
                    ExtRequestTargeting::getAlwaysincludedeals,
                    AccountTargetingConfig::getAlwaysIncludeDeals);
        }

        private <T> UpdateResult<T> resolveValue(Function<ExtRequestTargeting, T> originalExtractor,
                                                 Function<AccountTargetingConfig, T> accountExtractor) {

            final T originalValue = targeting != null ? originalExtractor.apply(targeting) : null;
            if (originalValue != null) {
                return UpdateResult.unaltered(originalValue);
            }

            final T accountValue = accountExtractor.apply(accountTargetingConfig);
            return accountValue != null ? UpdateResult.updated(accountValue) : UpdateResult.unaltered(null);
        }
    }
}
