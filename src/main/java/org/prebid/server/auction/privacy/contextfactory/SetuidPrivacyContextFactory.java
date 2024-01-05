package org.prebid.server.auction.privacy.contextfactory;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.execution.Timeout;
import org.prebid.server.metric.MetricName;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.RequestLogInfo;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;

import java.util.List;
import java.util.Objects;

public class SetuidPrivacyContextFactory {

    private final PrivacyExtractor privacyExtractor;
    private final TcfDefinerService tcfDefinerService;
    private final ImplicitParametersExtractor implicitParametersExtractor;
    private final IpAddressHelper ipAddressHelper;

    public SetuidPrivacyContextFactory(PrivacyExtractor privacyExtractor,
                                       TcfDefinerService tcfDefinerService,
                                       ImplicitParametersExtractor implicitParametersExtractor,
                                       IpAddressHelper ipAddressHelper) {

        this.privacyExtractor = Objects.requireNonNull(privacyExtractor);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.implicitParametersExtractor = Objects.requireNonNull(implicitParametersExtractor);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
    }

    public Future<PrivacyContext> contextFrom(HttpServerRequest httpRequest, Account account, Timeout timeout) {
        final Privacy privacy = privacyExtractor.validPrivacyFromSetuidRequest(httpRequest);
        return tcfDefinerService.resolveTcfContext(
                        privacy,
                        resolveIpFromRequest(httpRequest),
                        accountGdprConfig(account),
                        MetricName.setuid,
                        RequestLogInfo.of(MetricName.setuid, null, account.getId()),
                        timeout)
                .map(tcfContext -> PrivacyContext.of(privacy, tcfContext));
    }

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

    private static AccountGdprConfig accountGdprConfig(Account account) {
        final AccountPrivacyConfig privacyConfig = account.getPrivacy();
        return privacyConfig != null ? privacyConfig.getGdpr() : null;
    }
}
