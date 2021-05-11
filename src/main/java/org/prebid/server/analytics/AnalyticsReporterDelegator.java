package org.prebid.server.analytics;

import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Class dispatches event processing to all enabled reporters.
 */
public class AnalyticsReporterDelegator {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsReporterDelegator.class);
    private static final Set<String> ADAPTERS_PERMITTED_FOR_FULL_DATA = new HashSet<>(Arrays.asList("logAnalytics"));

    private final List<AnalyticsReporter> delegates;
    private final Vertx vertx;
    private final PrivacyEnforcementService privacyEnforcementService;

    private final Set<Integer> reporterVendorIds;
    private final Set<String> reporterNames;

    public AnalyticsReporterDelegator(List<AnalyticsReporter> delegates,
                                      Vertx vertx,
                                      PrivacyEnforcementService privacyEnforcementService) {
        this.delegates = Objects.requireNonNull(delegates);
        this.vertx = Objects.requireNonNull(vertx);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);

        reporterVendorIds = delegates.stream().map(AnalyticsReporter::vendorId).collect(Collectors.toSet());
        reporterNames = delegates.stream().map(AnalyticsReporter::name).collect(Collectors.toSet());
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
                prepareEvent(event, analyticsReporter.name());
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

    private <T> void validateEvent(T event) {
        if (event instanceof AuctionEvent) {
            validateExtPrebidAnalytics((AuctionEvent) event);
        }
    }

    private void validateExtPrebidAnalytics(AuctionEvent auctionEvent) {
        final ExtRequest requestExt = auctionEvent.getAuctionContext().getBidRequest().getExt();
        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final ObjectNode analytics = extPrebid != null ? extPrebid.getAnalytics() : null;
        final Iterator<String> analyticsFieldNames = analytics != null && !analytics.isEmpty()
                ? analytics.fieldNames() : null;

        if (analyticsFieldNames != null) {
            final List<String> unknownAdapterNames = StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(analyticsFieldNames, Spliterator.ORDERED), false)
                    .filter(adapter -> !reporterNames.contains(adapter))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(unknownAdapterNames)) {
                logger.warn(
                        String.format("Unknown adapters in ext.prebid.analytics[].adapter: %s", analyticsFieldNames));
            }
        }
    }

    private static <T> void prepareEvent(T event, String delegatorAdapter) {
        if (!ADAPTERS_PERMITTED_FOR_FULL_DATA.contains(delegatorAdapter) && event instanceof AuctionEvent) {
            final AuctionEvent auctionEvent = (AuctionEvent) event;
            updateAuctionContextForDelegator(auctionEvent.getAuctionContext(), delegatorAdapter);
        }
    }

    private static void updateAuctionContextForDelegator(AuctionContext context, String delegatorAdapter) {
        updateBidRequest(context.getBidRequest(), delegatorAdapter);
    }

    private static void updateBidRequest(BidRequest bidRequest, String adapterName) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final ObjectNode analytics = extPrebid != null ? extPrebid.getAnalytics() : null;
        if (analytics != null && !analytics.isEmpty()) {
            prepareAnalytics(analytics, adapterName);
        }
    }

    private static void prepareAnalytics(ObjectNode analytics, String adapterName) {
        analytics.fieldNames().forEachRemaining(fieldName -> {
            if (!Objects.equals(adapterName, fieldName)) {
                analytics.remove(fieldName);
            }
        });
    }
}
