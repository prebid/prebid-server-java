package org.prebid.server.auction.requestfactory;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.RejectedRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.HttpRequestWrapper;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class Ortb2RequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(Ortb2RequestFactory.class);

    private static final ConditionalLogger EMPTY_ACCOUNT_LOGGER = new ConditionalLogger("empty_account", logger);
    private static final ConditionalLogger UNKNOWN_ACCOUNT_LOGGER = new ConditionalLogger("unknown_account", logger);

    private final boolean enforceValidAccount;
    private final List<String> blacklistedAccounts;
    private final UidsCookieService uidsCookieService;
    private final RequestValidator requestValidator;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final ApplicationSettings applicationSettings;
    private final HookStageExecutor hookStageExecutor;

    public Ortb2RequestFactory(boolean enforceValidAccount,
                               List<String> blacklistedAccounts,
                               UidsCookieService uidsCookieService,
                               RequestValidator requestValidator,
                               TimeoutResolver timeoutResolver,
                               TimeoutFactory timeoutFactory,
                               ApplicationSettings applicationSettings,
                               HookStageExecutor hookStageExecutor) {

        this.enforceValidAccount = enforceValidAccount;
        this.blacklistedAccounts = Objects.requireNonNull(blacklistedAccounts);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
    }

    public AuctionContext createAuctionContext(HttpRequestWrapper httpRequest,
                                               BidRequest bidRequest,
                                               MetricName requestTypeMetric,
                                               long startTime,
                                               HookExecutionContext hookExecutionContext,
                                               List<String> errors) {

        return AuctionContext.builder()
                .httpRequest(httpRequest)
                .uidsCookie(uidsCookieService.parseFromRequest(httpRequest))
                .bidRequest(bidRequest)
                .requestTypeMetric(requestTypeMetric)
                .timeout(timeout(bidRequest, startTime))
                .prebidErrors(errors)
                .debugWarnings(new ArrayList<>())
                .hookExecutionContext(hookExecutionContext)
                .debugEnabled(debugEnabled(bidRequest))
                .build();
    }

    public Future<Account> fetchAccount(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Timeout timeout = auctionContext.getTimeout();
        final HttpRequestWrapper httpRequest = auctionContext.getHttpRequest();

        final String accountId = accountIdFrom(bidRequest);
        final boolean isAccountIdBlank = StringUtils.isBlank(accountId);

        if (CollectionUtils.isNotEmpty(blacklistedAccounts)
                && !isAccountIdBlank
                && blacklistedAccounts.contains(accountId)) {
            return Future.failedFuture(new BlacklistedAccountException(
                    String.format("Prebid-server has blacklisted Account ID: %s, please "
                            + "reach out to the prebid server host.", accountId)));
        }

        return isAccountIdBlank
                ? responseForEmptyAccount(httpRequest)
                : applicationSettings.getAccountById(accountId, timeout)
                .compose(this::ensureAccountActive,
                        exception -> accountFallback(exception, accountId, httpRequest));
    }

    /**
     * Performs thorough validation of fully constructed {@link BidRequest} that is going to be used to hold an auction.
     */
    public BidRequest validateRequest(BidRequest bidRequest) {
        final ValidationResult validationResult = requestValidator.validate(bidRequest);
        if (validationResult.hasErrors()) {
            throw new InvalidRequestException(validationResult.getErrors());
        }
        return bidRequest;
    }

    public BidRequest enrichBidRequestWithAccountAndPrivacyData(BidRequest bidRequest,
                                                                Account account,
                                                                PrivacyContext privacyContext) {

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

    public Future<HttpRequestWrapper> executeEntrypointHooks(RoutingContext routingContext,
                                                             String body,
                                                             HookExecutionContext hookExecutionContext) {

        return hookStageExecutor.executeEntrypointStage(
                routingContext.queryParams(),
                routingContext.request().headers(),
                body,
                hookExecutionContext)
                .map(stageResult -> toHttpRequest(stageResult, routingContext, hookExecutionContext));
    }

    public Future<BidRequest> executeRawAuctionRequestHooks(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final HookExecutionContext hookExecutionContext = auctionContext.getHookExecutionContext();

        return hookStageExecutor.executeRawAuctionRequestStage(bidRequest, account, hookExecutionContext)
                .map(stageResult -> toBidRequest(stageResult, auctionContext));
    }

    public Future<BidRequest> executeProcessedAuctionRequestHooks(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final HookExecutionContext hookExecutionContext = auctionContext.getHookExecutionContext();

        return hookStageExecutor.executeProcessedAuctionRequestStage(bidRequest, account, hookExecutionContext)
                .map(stageResult -> toBidRequest(stageResult, auctionContext));
    }

    private static HttpRequestWrapper toHttpRequest(HookStageExecutionResult<EntrypointPayload> stageResult,
                                                    RoutingContext routingContext,
                                                    HookExecutionContext hookExecutionContext) {

        if (stageResult.isShouldReject()) {
            throw new RejectedRequestException(hookExecutionContext);
        }

        return HttpRequestWrapper.builder()
                .absoluteUri(routingContext.request().absoluteURI())
                .queryParams(stageResult.getPayload().queryParams())
                .headers(stageResult.getPayload().headers())
                .cookies(routingContext.cookieMap())
                .body(stageResult.getPayload().body())
                .scheme(routingContext.request().scheme())
                .remoteHost(routingContext.request().remoteAddress().host())
                .build();
    }

    private static BidRequest toBidRequest(HookStageExecutionResult<AuctionRequestPayload> stageResult,
                                           AuctionContext auctionContext) {

        if (stageResult.isShouldReject()) {
            throw new RejectedRequestException(
                    auctionContext.getHookExecutionContext(), auctionContext.isDebugEnabled());
        }

        return stageResult.getPayload().bidRequest();
    }

    private static boolean debugEnabled(BidRequest bidRequest) {
        // FIXME: flesh out debug flag determination logic
        return false;
    }

    /**
     * Returns {@link Timeout} based on request.tmax and adjustment value of {@link TimeoutResolver}.
     */
    private Timeout timeout(BidRequest bidRequest, long startTime) {
        final long resolvedRequestTimeout = timeoutResolver.resolve(bidRequest.getTmax());
        final long timeout = timeoutResolver.adjustTimeout(resolvedRequestTimeout);
        return timeoutFactory.create(startTime, timeout);
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

    private Future<Account> responseForEmptyAccount(HttpRequestWrapper httpRequest) {
        EMPTY_ACCOUNT_LOGGER.warn(accountErrorMessage("Account not specified", httpRequest), 100);
        return responseForUnknownAccount(StringUtils.EMPTY);
    }

    private static String accountErrorMessage(String message, HttpRequestWrapper httpRequest) {
        return String.format(
                "%s, Url: %s and Referer: %s",
                message,
                httpRequest.getAbsoluteUri(),
                httpRequest.getHeaders().get(HttpUtil.REFERER_HEADER));
    }

    private Future<Account> accountFallback(Throwable exception,
                                            String accountId,
                                            HttpRequestWrapper httpRequest) {

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
        final ExtRequestPrebid prebidExt = getIfNotNull(ext, ExtRequest::getPrebid);
        final String integration = getIfNotNull(prebidExt, ExtRequestPrebid::getIntegration);
        final String accountDefaultIntegration = account.getDefaultIntegration();

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
        final String country = getIfNotNull(privacyContext.getTcfContext().getGeoInfo(), GeoInfo::getCountry);

        final String ipAddressInRequest = getIfNotNull(device, Device::getIp);

        final Geo geo = getIfNotNull(device, Device::getGeo);
        final String countryFromRequest = getIfNotNull(geo, Geo::getCountry);

        final boolean shouldUpdateIp = ipAddress != null && !Objects.equals(ipAddressInRequest, ipAddress);
        final boolean shouldUpdateCountry = country != null && !Objects.equals(countryFromRequest, country);

        if (shouldUpdateIp || shouldUpdateCountry) {
            final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

            if (shouldUpdateIp) {
                deviceBuilder.ip(ipAddress);
            }

            if (shouldUpdateCountry) {
                final Geo.GeoBuilder geoBuilder = geo != null ? geo.toBuilder() : Geo.builder();
                geoBuilder.country(country);
                deviceBuilder.geo(geoBuilder.build());
            }

            return deviceBuilder.build();
        }

        return null;
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
