package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.PrivacyAnonymizationService;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.VendorIdResolver;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.RequestLogInfo;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCcpaConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service provides masking for OpenRTB client sensitive information.
 */
public class PrivacyEnforcementService {

    private static final String CATCH_ALL_BIDDERS = "*";

    private final BidderCatalog bidderCatalog;
    private final PrivacyExtractor privacyExtractor;
    private final PrivacyAnonymizationService privacyAnonymizationService;
    private final TcfDefinerService tcfDefinerService;
    private final ImplicitParametersExtractor implicitParametersExtractor;
    private final IpAddressHelper ipAddressHelper;
    private final Metrics metrics;
    private final CountryCodeMapper countryCodeMapper;
    private final boolean ccpaEnforce;
    private final boolean lmtEnforce;

    public PrivacyEnforcementService(BidderCatalog bidderCatalog,
                                     PrivacyExtractor privacyExtractor,
                                     PrivacyAnonymizationService privacyAnonymizationService,
                                     TcfDefinerService tcfDefinerService,
                                     ImplicitParametersExtractor implicitParametersExtractor,
                                     IpAddressHelper ipAddressHelper,
                                     Metrics metrics,
                                     CountryCodeMapper countryCodeMapper,
                                     boolean ccpaEnforce,
                                     boolean lmtEnforce) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.privacyExtractor = Objects.requireNonNull(privacyExtractor);
        this.privacyAnonymizationService = Objects.requireNonNull(privacyAnonymizationService);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.implicitParametersExtractor = Objects.requireNonNull(implicitParametersExtractor);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.metrics = Objects.requireNonNull(metrics);
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
        this.ccpaEnforce = ccpaEnforce;
        this.lmtEnforce = lmtEnforce;
    }

    public Future<PrivacyContext> contextFromSetuidRequest(HttpServerRequest httpRequest,
                                                           Account account,
                                                           Timeout timeout) {

        final Privacy privacy = privacyExtractor.validPrivacyFromSetuidRequest(httpRequest);
        final String ipAddress = resolveIpFromRequest(httpRequest);
        final AccountGdprConfig accountGdpr = accountGdprConfig(account);
        final String accountId = account.getId();
        final RequestLogInfo requestLogInfo = requestLogInfo(MetricName.setuid, null, accountId);

        return tcfDefinerService.resolveTcfContext(
                privacy, ipAddress, accountGdpr, MetricName.setuid, requestLogInfo, timeout)
                .map(tcfContext -> PrivacyContext.of(privacy, tcfContext));
    }

    public Future<PrivacyContext> contextFromCookieSyncRequest(CookieSyncRequest cookieSyncRequest,
                                                               HttpServerRequest httpRequest,
                                                               Account account,
                                                               Timeout timeout) {

        final Privacy privacy = privacyExtractor.extractValidPrivacyFrom(cookieSyncRequest);
        final String ipAddress = resolveIpFromRequest(httpRequest);
        final AccountGdprConfig accountGdpr = accountGdprConfig(account);
        final String accountId = account.getId();
        final RequestLogInfo requestLogInfo = requestLogInfo(MetricName.cookiesync, null, accountId);

        return tcfDefinerService.resolveTcfContext(
                privacy, ipAddress, accountGdpr, MetricName.cookiesync, requestLogInfo, timeout)
                .map(tcfContext -> PrivacyContext.of(privacy, tcfContext));
    }

    // ---------------------------------------------------------

    private String resolveIpFromRequest(HttpServerRequest request) {
        final MultiMap headers = request.headers();
        final String host = request.remoteAddress().host();
        final List<String> requestIps = implicitParametersExtractor.ipFrom(headers, host);
        return requestIps.stream()
                .map(ipAddressHelper::toIpAddress)
                .filter(Objects::nonNull)
                .map(IpAddress::getIp)
                .findFirst()
                .orElse(null);
    }

    private static RequestLogInfo requestLogInfo(MetricName requestType, BidRequest bidRequest, String accountId) {
        if (Objects.equals(requestType, MetricName.openrtb2web)) {
            final Site site = bidRequest != null ? bidRequest.getSite() : null;
            final String refUrl = site != null ? site.getRef() : null;
            return RequestLogInfo.of(requestType, refUrl, accountId);
        }

        return RequestLogInfo.of(requestType, null, accountId);
    }

    public Future<List<BidderPrivacyResult>> mask(AuctionContext auctionContext,
                                                  Map<String, User> bidderToUser,
                                                  List<String> bidders,
                                                  BidderAliases aliases) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final MetricName requestType = auctionContext.getRequestTypeMetric();
        final Device device = bidRequest.getDevice();
        final PrivacyContext privacyContext = auctionContext.getPrivacyContext();

        final Privacy privacy = privacyContext.getPrivacy();

        // For now, COPPA masks all values, so we can omit GDPR masking.
        if (isCoppaMaskingRequired(privacy)) {
            return Future.succeededFuture(maskCoppa(bidderToUser, device));
        }

        final Map<String, BidderPrivacyResult> ccpaResult = maskCcpa(
                bidRequest, bidders, aliases, privacy, account, device, bidderToUser, requestType);

        final Set<String> biddersToApplyTcf = new HashSet<>(bidders);
        biddersToApplyTcf.removeAll(ccpaResult.keySet());

        final Future<List<BidderPrivacyResult>> tcfResult = maskTcf(
                privacyContext.getTcfContext(), biddersToApplyTcf, aliases, account, bidderToUser, requestType, device);

        return tcfResult.map(gdprResult -> merge(ccpaResult.values(), gdprResult));
    }

    public boolean isCcpaEnforced(Ccpa ccpa, Account account) {
        final boolean shouldEnforceCcpa = isCcpaEnabled(account);
        return shouldEnforceCcpa && ccpa.isEnforced();
    }

    private boolean isCcpaEnforced(Ccpa ccpa, Account account, MetricName requestType) {
        final boolean shouldEnforceCcpa = isCcpaEnabled(account, requestType);
        return shouldEnforceCcpa && ccpa.isEnforced();
    }

    private Boolean isCcpaEnabled(Account account) {
        final AccountPrivacyConfig accountPrivacyConfig = account.getPrivacy();
        final AccountCcpaConfig accountCcpaConfig =
                accountPrivacyConfig != null ? accountPrivacyConfig.getCcpa() : null;
        final Boolean accountCcpaEnabled = accountCcpaConfig != null ? accountCcpaConfig.getEnabled() : null;

        return ObjectUtils.defaultIfNull(accountCcpaEnabled, ccpaEnforce);
    }

    private boolean isCcpaEnabled(Account account, MetricName requestType) {
        final AccountPrivacyConfig accountPrivacyConfig = account.getPrivacy();
        final AccountCcpaConfig accountCcpaConfig =
                accountPrivacyConfig != null ? accountPrivacyConfig.getCcpa() : null;

        final Boolean accountCcpaEnabled = accountCcpaConfig != null ? accountCcpaConfig.getEnabled() : null;
        if (requestType == null) {
            return ObjectUtils.defaultIfNull(accountCcpaEnabled, ccpaEnforce);
        }

        final EnabledForRequestType enabledForRequestType = accountCcpaConfig != null
                ? accountCcpaConfig.getEnabledForRequestType()
                : null;

        final Boolean enabledForType = enabledForRequestType != null
                ? enabledForRequestType.isEnabledFor(requestType)
                : null;
        return ObjectUtils.firstNonNull(enabledForType, accountCcpaEnabled, ccpaEnforce);
    }

    private Map<String, BidderPrivacyResult> maskCcpa(BidRequest bidRequest,
                                                      List<String> bidders,
                                                      BidderAliases aliases,
                                                      Privacy privacy,
                                                      Account account,
                                                      Device device,
                                                      Map<String, User> bidderToUser,
                                                      MetricName requestType) {

        updateCcpaMetrics(privacy.getCcpa());
        return isCcpaEnforced(privacy.getCcpa(), account, requestType)
                ? maskCcpa(extractCcpaEnforcedBidders(bidders, bidRequest, aliases), device, bidderToUser)
                : Collections.emptyMap();
    }

    private Map<String, BidderPrivacyResult> maskCcpa(Set<String> biddersToMask,
                                                      Device device,
                                                      Map<String, User> bidderToUser) {

        return biddersToMask.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        bidder -> privacyAnonymizationService.maskCcpa(bidderToUser.get(bidder), device, bidder)));
    }

    private Set<String> extractCcpaEnforcedBidders(List<String> bidders,
                                                   BidRequest bidRequest,
                                                   BidderAliases aliases) {

        final Set<String> ccpaEnforcedBidders = new HashSet<>(bidders);

        final ExtRequest extBidRequest = bidRequest.getExt();
        final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;
        final List<String> nosaleBidders = extRequestPrebid != null
                ? ListUtils.emptyIfNull(extRequestPrebid.getNosale())
                : Collections.emptyList();

        if (nosaleBidders.size() == 1 && nosaleBidders.contains(CATCH_ALL_BIDDERS)) {
            ccpaEnforcedBidders.clear();
        } else {
            nosaleBidders.forEach(ccpaEnforcedBidders::remove);
        }

        ccpaEnforcedBidders.removeIf(bidder ->
                !bidderCatalog.bidderInfoByName(aliases.resolveBidder(bidder)).isCcpaEnforced());

        return ccpaEnforcedBidders;
    }

    private void updateCcpaMetrics(Ccpa ccpa) {
        metrics.updatePrivacyCcpaMetrics(ccpa.isNotEmpty(), ccpa.isEnforced());
    }

    private static boolean isCoppaMaskingRequired(Privacy privacy) {
        return privacy.getCoppa() == 1;
    }

    private List<BidderPrivacyResult> maskCoppa(Map<String, User> bidderToUser, Device device) {
        metrics.updatePrivacyCoppaMetric();

        return bidderToUser.entrySet().stream()
                .map(bidderAndUser ->
                        privacyAnonymizationService.maskCoppa(bidderAndUser.getValue(), device, bidderAndUser.getKey()))
                .collect(Collectors.toList());
    }

    private Future<List<BidderPrivacyResult>> maskTcf(TcfContext tcfContext,
                                                      Set<String> biddersToApplyTcf,
                                                      BidderAliases aliases,
                                                      Account account,
                                                      Map<String, User> bidderToUser,
                                                      MetricName requestType,
                                                      Device device) {

        return getBidderToEnforcementAction(tcfContext, biddersToApplyTcf, aliases, account)
                .map(bidderToEnforcement -> getBidderToPrivacyResult(
                        bidderToEnforcement, biddersToApplyTcf, aliases, bidderToUser, device, requestType));
    }

    /**
     * Returns {@link Future &lt;{@link Map}&lt;{@link String}, {@link PrivacyEnforcementAction}&gt;&gt;},
     * where bidder names mapped to actions for GDPR masking for pbs server.
     */
    private Future<Map<String, PrivacyEnforcementAction>> getBidderToEnforcementAction(TcfContext tcfContext,
                                                                                       Set<String> bidders,
                                                                                       BidderAliases aliases,
                                                                                       Account account) {

        return tcfDefinerService.resultForBidderNames(
                Collections.unmodifiableSet(bidders),
                VendorIdResolver.of(aliases, bidderCatalog),
                tcfContext,
                accountGdprConfig(account))
                .map(tcfResponse -> mapTcfResponseToEachBidder(tcfResponse, bidders));
    }

    private static Map<String, PrivacyEnforcementAction> mapTcfResponseToEachBidder(TcfResponse<String> tcfResponse,
                                                                                    Set<String> bidders) {

        final Map<String, PrivacyEnforcementAction> bidderNameToAction = tcfResponse.getActions();
        return bidders.stream()
                .collect(Collectors.toMap(Function.identity(), bidderNameToAction::get));
    }

    /**
     * Returns {@link Map}&lt;{@link String}, {@link BidderPrivacyResult}&gt;, where bidder name mapped to masked
     * {@link BidderPrivacyResult}. Masking depends on GDPR and COPPA.
     */
    private List<BidderPrivacyResult> getBidderToPrivacyResult(
            Map<String, PrivacyEnforcementAction> bidderToEnforcement,
            Set<String> bidders,
            BidderAliases bidderAliases,
            Map<String, User> bidderToUser,
            Device device,
            MetricName requestType) {

        if (lmtEnforce && isLmtEnabled(device)) {
            metrics.updatePrivacyLmtMetric();
        }

        return bidderToUser.entrySet().stream()
                .filter(entry -> bidders.contains(entry.getKey()))
                .map(bidderUserEntry -> privacyAnonymizationService.maskTcf(
                        bidderUserEntry.getValue(),
                        device,
                        bidderUserEntry.getKey(),
                        bidderAliases,
                        requestType,
                        bidderToEnforcement.get(bidderUserEntry.getKey())))
                .collect(Collectors.toList());
    }

    public Future<Map<Integer, PrivacyEnforcementAction>> resultForVendorIds(Set<Integer> vendorIds,
                                                                             TcfContext tcfContext) {

        return tcfDefinerService.resultForVendorIds(vendorIds, tcfContext).map(TcfResponse::getActions);
    }

    private static boolean isLmtEnabled(Device device) {
        return device != null && Objects.equals(device.getLmt(), 1);
    }

    private static List<BidderPrivacyResult> merge(
            Collection<BidderPrivacyResult> ccpaResult,
            Collection<BidderPrivacyResult> gdprResult) {

        final List<BidderPrivacyResult> results = new ArrayList<>(ccpaResult);
        results.addAll(gdprResult);
        return results;
    }

    private static AccountGdprConfig accountGdprConfig(Account account) {
        final AccountPrivacyConfig privacyConfig = account.getPrivacy();
        return privacyConfig != null ? privacyConfig.getGdpr() : null;
    }
}
