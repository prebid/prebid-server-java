package org.prebid.server.auction.privacycontextfactory;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.RequestLogInfo;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.Objects;

public class AmpPrivacyContextFactory {

    private static final String CONSENT_TYPE_PARAM = "consent_type";

    private final PrivacyExtractor privacyExtractor;
    private final TcfDefinerService tcfDefinerService;
    private final IpAddressHelper ipAddressHelper;
    private final CountryCodeMapper countryCodeMapper;

    public AmpPrivacyContextFactory(PrivacyExtractor privacyExtractor,
                                    TcfDefinerService tcfDefinerService,
                                    IpAddressHelper ipAddressHelper,
                                    CountryCodeMapper countryCodeMapper) {

        this.privacyExtractor = Objects.requireNonNull(privacyExtractor);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
    }

    public Future<PrivacyContext> contextFrom(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();

        final MetricName requestType = auctionContext.getRequestTypeMetric();
        final Timeout timeout = auctionContext.getTimeout();
        final List<String> errors = auctionContext.getPrebidErrors();

        final Privacy initialPrivacy = privacyExtractor.validPrivacyFrom(bidRequest, errors);
        final Privacy strippedPrivacy = stripPrivacy(initialPrivacy, auctionContext);

        final Device device = bidRequest.getDevice();

        final Account account = auctionContext.getAccount();
        final AccountPrivacyConfig accountPrivacyConfig = ObjectUtil.getIfNotNull(account, Account::getPrivacy);

        return tcfDefinerService.resolveTcfContext(
                strippedPrivacy,
                resolveAlpha2CountryCode(device),
                resolveIpAddress(device, strippedPrivacy),
                ObjectUtil.getIfNotNull(accountPrivacyConfig, AccountPrivacyConfig::getGdpr),
                requestType,
                requestLogInfo(requestType, bidRequest, account.getId()),
                timeout)
                .map(tcfContext -> logWarnings(auctionContext, tcfContext))
                .map(tcfContext -> PrivacyContext.of(strippedPrivacy, tcfContext, tcfContext.getIpAddress()));
    }

    private Privacy stripPrivacy(Privacy privacy, AuctionContext auctionContext) {
        final String consentType = StringUtils.defaultString(
                auctionContext.getHttpRequest().getQueryParams().get(CONSENT_TYPE_PARAM));

        if (!StringUtils.equalsAny(consentType, "", "1", "2", "3")) {
            auctionContext.getPrebidErrors().add("Invalid consent_type param passed");
            return removeConsentString(privacy);
        } else if (StringUtils.equals(consentType, "1")) {
            auctionContext.getPrebidErrors().add("Consent type tcfV1 is no longer supported");
            return removeConsentString(privacy);
        }

        return privacy;
    }

    private Privacy removeConsentString(Privacy privacy) {
        return privacyExtractor.toValidPrivacy(
                privacy.getGdpr(),
                null,
                privacy.getCcpa().getUsPrivacy(),
                privacy.getCoppa(),
                null);
    }

    private static TcfContext logWarnings(AuctionContext auctionContext, TcfContext tcfContext) {
        auctionContext.getDebugWarnings().addAll(tcfContext.getWarnings());

        return tcfContext;
    }

    private String resolveAlpha2CountryCode(Device device) {
        final Geo geo = device != null ? device.getGeo() : null;
        final String alpha3CountryCode = geo != null ? geo.getCountry() : null;

        return countryCodeMapper.mapToAlpha2(alpha3CountryCode);
    }

    private String resolveIpAddress(Device device, Privacy privacy) {
        final boolean shouldBeMasked = Objects.equals(privacy.getCoppa(), 1)
                || (device != null && Objects.equals(device.getLmt(), 1));

        final String ipV4Address = device != null ? device.getIp() : null;
        if (StringUtils.isNotBlank(ipV4Address)) {
            return shouldBeMasked ? ipAddressHelper.maskIpv4(ipV4Address) : ipV4Address;
        }

        final String ipV6Address = device != null ? device.getIpv6() : null;
        if (StringUtils.isNotBlank(ipV6Address)) {
            return shouldBeMasked ? ipAddressHelper.anonymizeIpv6(ipV6Address) : ipV6Address;
        }

        return null;
    }

    private static RequestLogInfo requestLogInfo(MetricName requestType, BidRequest bidRequest, String accountId) {
        if (Objects.equals(requestType, MetricName.openrtb2web)) {
            final Site site = bidRequest != null ? bidRequest.getSite() : null;
            final String refUrl = site != null ? site.getRef() : null;
            return RequestLogInfo.of(requestType, refUrl, accountId);
        }

        return RequestLogInfo.of(requestType, null, accountId);
    }
}
