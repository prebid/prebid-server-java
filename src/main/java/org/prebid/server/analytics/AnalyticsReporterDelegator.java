package org.prebid.server.analytics;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAnalytic;

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
    private static final String RELATED_TO_ALL_ADAPTERS = "*";

    private final List<AnalyticsReporter> delegates;
    private final Vertx vertx;
    private final PrivacyEnforcementService privacyEnforcementService;

    private final Set<Integer> reporterVendorIds;
    private final Set<String> reporterAdapters;

    public AnalyticsReporterDelegator(List<AnalyticsReporter> delegates,
                                      Vertx vertx,
                                      PrivacyEnforcementService privacyEnforcementService) {
        this.delegates = Objects.requireNonNull(delegates);
        this.vertx = Objects.requireNonNull(vertx);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);

        reporterVendorIds = delegates.stream().map(AnalyticsReporter::vendorId).collect(Collectors.toSet());
        reporterAdapters = delegates.stream().map(AnalyticsReporter::adapter).collect(Collectors.toSet());
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
            validateEvent(event);
            for (AnalyticsReporter analyticsReporter : delegates) {
                final T preparedEvent = prepareEvent(event, analyticsReporter.adapter());
                final int reporterVendorId = analyticsReporter.vendorId();
                // resultForVendorIds is guaranteed returning for each provided value except null,
                // but to be sure lets use getOrDefault
                final PrivacyEnforcementAction reporterPrivacyAction = privacyEnforcementActionMap
                        .getOrDefault(reporterVendorId, PrivacyEnforcementAction.restrictAll());
                if (!reporterPrivacyAction.isBlockAnalyticsReport()) {
                    vertx.runOnContext(ignored -> analyticsReporter.processEvent(preparedEvent));
                }
            }

        } else {
            final Throwable privacyEnforcementException = privacyEnforcementMapResult.cause();
            logger.error("Analytics TCF enforcement check failed for consentString: {0} and "
                            + "delegates with vendorIds {1}", privacyEnforcementException,
                    tcfContext.getConsentString(), delegates);
        }
    }

    private <T> void validateEvent(T event) {
        if (event instanceof AuctionEvent) {
            validateExtPrebidAnalytics((AuctionEvent) event);
        }
    }

    private void validateExtPrebidAnalytics(AuctionEvent auctionEvent) {
        final ExtRequest requestExt = auctionEvent.getAuctionContext().getBidRequest().getExt();
        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final List<ExtRequestPrebidAnalytic> analytics = extPrebid != null ? extPrebid.getAnalytics() : null;
        final List<String> unknownAnalyticsAdapters = CollectionUtils.emptyIfNull(analytics).stream()
                .map(ExtRequestPrebidAnalytic::getAdapter)
                .filter(s -> !reporterAdapters.contains(s))
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(unknownAnalyticsAdapters)) {
            logger.warn(
                    String.format("Unknown adapters in ext.prebid.analytics[].adapter: %s", unknownAnalyticsAdapters));
        }
    }

    private <T> T prepareEvent(T event, String delegatorAdapter) {
        if (!RELATED_TO_ALL_ADAPTERS.equals(delegatorAdapter) && event instanceof AuctionEvent) {
            final AuctionEvent auctionEvent = (AuctionEvent) event;
            final AuctionContext updatedAuctionContext =
                    updateAuctionContextForDelegator(auctionEvent.getAuctionContext(), delegatorAdapter);

            return updatedAuctionContext != null
                    ? (T) auctionEvent.toBuilder().auctionContext(updatedAuctionContext).build()
                    : event;
        }
        return event;
    }

    private AuctionContext updateAuctionContextForDelegator(AuctionContext context, String delegatorAdapter) {
        final BidRequest updatedBidRequest = updateBidRequest(context.getBidRequest(), delegatorAdapter);

        return updatedBidRequest != null ? context.toBuilder()
                .bidRequest(updateBidRequest(context.getBidRequest(), delegatorAdapter))
                .build() : null;
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, String adapterName) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final List<ExtRequestPrebidAnalytic> analytics = extPrebid != null ? extPrebid.getAnalytics() : null;

        final List<ExtRequestPrebidAnalytic> adapterRelatedEntrys = CollectionUtils.emptyIfNull(analytics).stream()
                .filter(analytic -> Objects.equals(adapterName, analytic.getAdapter()))
                .collect(Collectors.toList());

        return analytics != null && !adapterRelatedEntrys.equals(analytics) ? bidRequest.toBuilder()
                .ext(ExtRequest.of(extPrebid.toBuilder()
                        .analytics(adapterRelatedEntrys)
                        .build()))
                .build() : null;
    }
}
