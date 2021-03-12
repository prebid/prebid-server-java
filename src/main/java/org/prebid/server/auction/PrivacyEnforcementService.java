package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.execution.Timeout;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
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
import org.prebid.server.privacy.model.PrivacyDebugLog;
import org.prebid.server.privacy.model.PrivacyExtractorResult;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service provides masking for OpenRTB user sensitive information.
 */
public class PrivacyEnforcementService {

    private static final String CATCH_ALL_BIDDERS = "*";

    private final BidderCatalog bidderCatalog;
    private final PrivacyExtractor privacyExtractor;
    private final PrivacyAnonymizationService privacyAnonymizationService;
    private final TcfDefinerService tcfDefinerService;
    private final IpAddressHelper ipAddressHelper;
    private final Metrics metrics;
    private final boolean ccpaEnforce;
    private final boolean lmtEnforce;

    public PrivacyEnforcementService(BidderCatalog bidderCatalog,
                                     PrivacyExtractor privacyExtractor,
                                     PrivacyAnonymizationService privacyAnonymizationService,
                                     TcfDefinerService tcfDefinerService,
                                     IpAddressHelper ipAddressHelper,
                                     Metrics metrics,
                                     boolean ccpaEnforce,
                                     boolean lmtEnforce) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.privacyExtractor = Objects.requireNonNull(privacyExtractor);
        this.privacyAnonymizationService = Objects.requireNonNull(privacyAnonymizationService);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.metrics = Objects.requireNonNull(metrics);
        this.ccpaEnforce = ccpaEnforce;
        this.lmtEnforce = lmtEnforce;
    }

    Future<PrivacyContext> contextFromBidRequest(
            BidRequest bidRequest, Account account, MetricName requestType, Timeout timeout, List<String> errors) {

        final PrivacyExtractorResult privacyExtractorResult = privacyExtractor.validPrivacyFrom(bidRequest);
        errors.addAll(privacyExtractorResult.getErrors());
        final Privacy privacy = privacyExtractorResult.getValidPrivacy();

        final Device device = bidRequest.getDevice();
        final String ipAddress = device != null ? device.getIp() : null;

        final Geo geo = device != null ? device.getGeo() : null;
        final String country = geo != null ? geo.getCountry() : null;

        final String effectiveIpAddress = isCoppaMaskingRequired(privacy) || isLmtEnabled(device)
                ? ipAddressHelper.maskIpv4(ipAddress)
                : ipAddress;

        final AccountGdprConfig accountGdpr = account.getGdpr();
        final String accountId = account.getId();
        final RequestLogInfo requestLogInfo = requestLogInfo(requestType, bidRequest, accountId);

        return tcfDefinerService.resolveTcfContext(
                privacy, country, effectiveIpAddress, accountGdpr, requestType, requestLogInfo, timeout)
                .map(tcfContext -> PrivacyContext.of(privacy, tcfContext, tcfContext.getIpAddress(),
                        PrivacyDebugLog.from(privacyExtractorResult.getOriginPrivacy(), privacy, tcfContext,
                                privacyExtractorResult.getErrors())));
    }

    public Future<PrivacyContext> contextFromSetuidRequest(
            HttpServerRequest httpRequest, Account account, Timeout timeout) {

        final Privacy privacy = privacyExtractor.validPrivacyFromSetuidRequest(httpRequest);
        final String ipAddress = HttpUtil.ipFrom(httpRequest);
        final AccountGdprConfig accountGdpr = account.getGdpr();
        final String accountId = account.getId();
        final RequestLogInfo requestLogInfo = requestLogInfo(MetricName.setuid, null, accountId);

        return tcfDefinerService.resolveTcfContext(
                privacy, ipAddress, accountGdpr, MetricName.setuid, requestLogInfo, timeout)
                .map(tcfContext -> PrivacyContext.of(privacy, tcfContext));
    }

    public Future<PrivacyContext> contextFromCookieSyncRequest(
            CookieSyncRequest cookieSyncRequest, HttpServerRequest httpRequest, Account account, Timeout timeout) {

        final Privacy privacy = privacyExtractor.validPrivacyFrom(cookieSyncRequest);
        final String ipAddress = HttpUtil.ipFrom(httpRequest);
        final AccountGdprConfig accountGdpr = account.getGdpr();
        final String accountId = account.getId();
        final RequestLogInfo requestLogInfo = requestLogInfo(MetricName.cookiesync, null, accountId);

        return tcfDefinerService.resolveTcfContext(
                privacy, ipAddress, accountGdpr, MetricName.cookiesync, requestLogInfo, timeout)
                .map(tcfContext -> PrivacyContext.of(privacy, tcfContext));
    }

    private static RequestLogInfo requestLogInfo(MetricName requestType, BidRequest bidRequest, String accountId) {
        if (Objects.equals(requestType, MetricName.openrtb2web)) {
            final Site site = bidRequest.getSite();
            final String refUrl = site != null ? site.getRef() : null;
            return RequestLogInfo.of(requestType, refUrl, accountId);
        }

        return RequestLogInfo.of(requestType, null, accountId);
    }

    Future<List<BidderPrivacyResult>> mask(AuctionContext auctionContext,
                                           Map<String, User> bidderToUser,
                                           List<String> bidders,
                                           BidderAliases aliases) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final MetricName requestType = auctionContext.getRequestTypeMetric();
        final Device device = bidRequest.getDevice();
        final PrivacyContext privacyContext = auctionContext.getPrivacyContext();

        final Privacy privacy = privacyContext.getPrivacy();

        // For now, COPPA masking all values, so we can omit GDPR masking.
        if (isCoppaMaskingRequired(privacy)) {
            return Future.succeededFuture(maskCoppa(bidderToUser, device));
        }

        updateCcpaMetrics(privacy.getCcpa());
        final Map<String, BidderPrivacyResult> ccpaResult =
                ccpaResult(bidRequest, account, bidders, aliases, device, bidderToUser, privacy);

        final Set<String> biddersToApplyTcf = new HashSet<>(bidders);
        biddersToApplyTcf.removeAll(ccpaResult.keySet());

        return getBidderToEnforcementAction(privacyContext.getTcfContext(), biddersToApplyTcf, aliases, account)
                .map(bidderToEnforcement -> getBidderToPrivacyResult(
                        bidderToEnforcement, biddersToApplyTcf, bidderToUser, requestType, device, aliases))
                .map(gdprResult -> merge(ccpaResult, gdprResult));
    }

    public Future<Map<Integer, PrivacyEnforcementAction>> resultForVendorIds(Set<Integer> vendorIds,
                                                                             TcfContext tcfContext) {
        return tcfDefinerService.resultForVendorIds(vendorIds, tcfContext)
                .map(TcfResponse::getActions);
    }

    private Map<String, BidderPrivacyResult> ccpaResult(BidRequest bidRequest,
                                                        Account account,
                                                        List<String> bidders,
                                                        BidderAliases aliases,
                                                        Device device,
                                                        Map<String, User> bidderToUser,
                                                        Privacy privacy) {

        if (isCcpaEnforced(privacy.getCcpa(), account)) {
            return maskCcpa(extractCcpaEnforcedBidders(bidders, bidRequest, aliases), device, bidderToUser);
        }

        return Collections.emptyMap();
    }

    public boolean isCcpaEnforced(Ccpa ccpa, Account account) {
        final boolean shouldEnforceCcpa = BooleanUtils.toBooleanDefaultIfNull(account.getEnforceCcpa(), ccpaEnforce);

        return shouldEnforceCcpa && ccpa.isEnforced();
    }

    private Map<String, BidderPrivacyResult> maskCcpa(Set<String> biddersToMask,
                                                      Device device,
                                                      Map<String, User> bidderToUser) {

        return biddersToMask.stream()
                .collect(Collectors.toMap(Function.identity(),
                        bidder -> privacyAnonymizationService.maskCcpa(bidderToUser.get(bidder), device, bidder)));
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

    /**
     * Returns {@link Future &lt;{@link Map}&lt;{@link String}, {@link PrivacyEnforcementAction}&gt;&gt;},
     * where bidder names mapped to actions for GDPR masking for pbs server.
     */
    private Future<Map<String, PrivacyEnforcementAction>> getBidderToEnforcementAction(
            TcfContext tcfContext, Set<String> bidders, BidderAliases aliases, Account account) {

        return tcfDefinerService.resultForBidderNames(
                Collections.unmodifiableSet(bidders), VendorIdResolver.of(aliases), tcfContext, account.getGdpr())
                .map(tcfResponse -> mapTcfResponseToEachBidder(tcfResponse, bidders));
    }

    private Set<String> extractCcpaEnforcedBidders(List<String> bidders, BidRequest bidRequest, BidderAliases aliases) {
        final Set<String> ccpaEnforcedBidders = new HashSet<>(bidders);

        final ExtRequest extBidRequest = bidRequest.getExt();
        final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;
        final List<String> nosaleBidders = extRequestPrebid != null
                ? ListUtils.emptyIfNull(extRequestPrebid.getNosale())
                : Collections.emptyList();

        if (nosaleBidders.size() == 1 && nosaleBidders.contains(CATCH_ALL_BIDDERS)) {
            ccpaEnforcedBidders.clear();
        } else {
            ccpaEnforcedBidders.removeAll(nosaleBidders);
        }

        ccpaEnforcedBidders.removeIf(bidder ->
                !bidderCatalog.bidderInfoByName(aliases.resolveBidder(bidder)).isCcpaEnforced());

        return ccpaEnforcedBidders;
    }

    private static Map<String, PrivacyEnforcementAction> mapTcfResponseToEachBidder(TcfResponse<String> tcfResponse,
                                                                                    Set<String> bidders) {

        final Map<String, PrivacyEnforcementAction> bidderNameToAction = tcfResponse.getActions();
        return bidders.stream().collect(Collectors.toMap(Function.identity(), bidderNameToAction::get));
    }

    private void updateCcpaMetrics(Ccpa ccpa) {
        metrics.updatePrivacyCcpaMetrics(ccpa.isNotEmpty(), ccpa.isEnforced());
    }

    /**
     * Returns {@link Map}&lt;{@link String}, {@link BidderPrivacyResult}&gt;, where bidder name mapped to masked
     * {@link BidderPrivacyResult}. Masking depends on GDPR and COPPA.
     */
    private List<BidderPrivacyResult> getBidderToPrivacyResult(
            Map<String, PrivacyEnforcementAction> bidderToEnforcement,
            Set<String> bidders,
            Map<String, User> bidderToUser,
            MetricName requestType,
            Device device,
            BidderAliases aliases) {

        if (lmtEnforce && isLmtEnabled(device)) {
            metrics.updatePrivacyLmtMetric();
        }

        return bidderToUser.entrySet().stream()
                .filter(entry -> bidders.contains(entry.getKey()))
                .map(bidderUserEntry -> privacyAnonymizationService.maskTcf(
                        bidderUserEntry.getValue(),
                        device,
                        bidderUserEntry.getKey(),
                        aliases,
                        requestType,
                        bidderToEnforcement.get(bidderUserEntry.getKey())))
                .collect(Collectors.toList());
    }

    private static boolean isLmtEnabled(Device device) {
        return device != null && Objects.equals(device.getLmt(), 1);
    }

    private static List<BidderPrivacyResult> merge(
            Map<String, BidderPrivacyResult> ccpaResult, List<BidderPrivacyResult> gdprResult) {

        final List<BidderPrivacyResult> result = new ArrayList<>(ccpaResult.values());
        result.addAll(gdprResult);
        return result;
    }
}
