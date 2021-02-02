package org.prebid.server.analytics;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class dispatches event processing to all enabled reporters.
 */
public class AnalyticsReporterDelegator {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsReporterDelegator.class);

    private final List<AnalyticsReporter> delegates;
    private final Vertx vertx;
    private final PrivacyEnforcementService privacyEnforcementService;

    private final Set<Integer> reporterVendorIds;

    public AnalyticsReporterDelegator(List<AnalyticsReporter> delegates,
                                      Vertx vertx,
                                      PrivacyEnforcementService privacyEnforcementService) {
        this.delegates = Objects.requireNonNull(delegates);
        this.vertx = Objects.requireNonNull(vertx);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);

        reporterVendorIds = delegates.stream().map(AnalyticsReporter::vendorId).collect(Collectors.toSet());
    }

    public <T> void processEvent(T event) {
        for (AnalyticsReporter analyticsReporter : delegates) {
            vertx.runOnContext(ignored -> analyticsReporter.processEvent(event));
        }
    }

    public <T> void processEvent(T event, TcfContext tcfContext) {
        privacyEnforcementService.resultForVendorIds(reporterVendorIds, tcfContext)
                .setHandler(privacyEnforcementMap -> delegateEvent(event, tcfContext, privacyEnforcementMap));
    }

    private <T> void delegateEvent(T event,
                                   TcfContext tcfContext,
                                   AsyncResult<Map<Integer, PrivacyEnforcementAction>> privacyEnforcementMapResult) {
        if (privacyEnforcementMapResult.succeeded()) {
            final Map<Integer, PrivacyEnforcementAction> privacyEnforcementActionMap =
                    privacyEnforcementMapResult.result();
            for (AnalyticsReporter analyticsReporter : delegates) {
                final int reporterVendorId = analyticsReporter.vendorId();
                // resultForVendorIds is guaranteed returning for each provided value except null,
                // but to be sure lets use getOrDefault
                final PrivacyEnforcementAction reporterPrivacyAction = privacyEnforcementActionMap
                        .getOrDefault(reporterVendorId, PrivacyEnforcementAction.restrictAll());
                if (!reporterPrivacyAction.isBlockAnalyticsReport()) {
                    vertx.runOnContext(ignored -> analyticsReporter.processEvent(event));
                }
            }

        } else {
            final Throwable privacyEnforcementException = privacyEnforcementMapResult.cause();
            logger.error("Analytics TCF enforcement check failed for consentString: {0} and "
                            + "delegates with vendorIds {1}", privacyEnforcementException,
                    tcfContext.getConsentString(), delegates);
        }
    }
}
