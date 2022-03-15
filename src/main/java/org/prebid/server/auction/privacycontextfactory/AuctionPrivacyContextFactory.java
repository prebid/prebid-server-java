package org.prebid.server.auction.privacycontextfactory;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.RequestLogInfo;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.privacy.model.PrivacyResult;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;

import java.util.List;
import java.util.Objects;

public class AuctionPrivacyContextFactory {

    private final PrivacyExtractor privacyExtractor;
    private final TcfDefinerService tcfDefinerService;
    private final IpAddressHelper ipAddressHelper;
    private final CountryCodeMapper countryCodeMapper;

    public AuctionPrivacyContextFactory(PrivacyExtractor privacyExtractor,
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

        final Account account = auctionContext.getAccount();
        final MetricName requestType = auctionContext.getRequestTypeMetric();
        final Device device = bidRequest.getDevice();

        final PrivacyResult privacy = privacyExtractor.extractPrivacyFrom(bidRequest);
        auctionContext.getPrebidErrors().addAll(privacy.getErrors());

        final Privacy validPrivacy = privacy.getValidPrivacy();

        return tcfDefinerService.resolveTcfContext(
                validPrivacy,
                resolveAlpha2CountryCode(device),
                resolveIpAddress(device, validPrivacy),
                accountGdprConfig(account),
                requestType,
                requestLogInfo(requestType, bidRequest, account.getId()),
                auctionContext.getTimeout())
                .map(tcfContext -> logWarnings(auctionContext.getDebugWarnings(), tcfContext))
                .map(tcfContext -> PrivacyContext.from(tcfContext, privacy));
    }

    private static TcfContext logWarnings(List<String> debugWarnings, TcfContext tcfContext) {
        debugWarnings.addAll(tcfContext.getWarnings());

        return tcfContext;
    }

    private String resolveAlpha2CountryCode(Device device) {
        final Geo geo = device != null ? device.getGeo() : null;
        final String alpha3CountryCode = geo != null ? geo.getCountry() : null;

        return countryCodeMapper.mapToAlpha2(alpha3CountryCode);
    }

    private String resolveIpAddress(Device device, Privacy privacy) {
        final boolean shouldBeMasked = privacy.getCoppa() == 1
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
        if (requestType == MetricName.openrtb2web) {
            final Site site = bidRequest != null ? bidRequest.getSite() : null;
            final String refUrl = site != null ? site.getRef() : null;
            return RequestLogInfo.of(requestType, refUrl, accountId);
        }

        return RequestLogInfo.of(requestType, null, accountId);
    }

    private static AccountGdprConfig accountGdprConfig(Account account) {
        final AccountPrivacyConfig privacyConfig = account.getPrivacy();
        return privacyConfig != null ? privacyConfig.getGdpr() : null;
    }
}
