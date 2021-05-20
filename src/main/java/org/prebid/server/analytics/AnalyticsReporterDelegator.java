package org.prebid.server.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Site;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.util.StreamUtil;

import java.util.Collections;
import java.util.Iterator;
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
    private static final ConditionalLogger UNKNOWN_ADAPTERS_LOGGER = new ConditionalLogger(logger);
    private static final Set<String> ADAPTERS_PERMITTED_FOR_FULL_DATA = Collections.singleton("logAnalytics");

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
            checkUnknownAdaptersForAuctionEvent(event);
            for (AnalyticsReporter analyticsReporter : delegates) {
                final T updatedEvent = updateEvent(event, analyticsReporter.name());
                final int reporterVendorId = analyticsReporter.vendorId();
                // resultForVendorIds is guaranteed returning for each provided value except null,
                // but to be sure lets use getOrDefault
                final PrivacyEnforcementAction reporterPrivacyAction = privacyEnforcementActionMap
                        .getOrDefault(reporterVendorId, PrivacyEnforcementAction.restrictAll());
                if (!reporterPrivacyAction.isBlockAnalyticsReport()) {
                    vertx.runOnContext(ignored -> analyticsReporter.processEvent(updatedEvent));
                }
            }
        } else {
            final Throwable privacyEnforcementException = privacyEnforcementMapResult.cause();
            logger.error("Analytics TCF enforcement check failed for consentString: {0} and "
                            + "delegates with vendorIds {1}", privacyEnforcementException,
                    tcfContext.getConsentString(), delegates);
        }
    }

    private <T> void checkUnknownAdaptersForAuctionEvent(T event) {
        if (event instanceof AuctionEvent) {
            logUnknownAdapters((AuctionEvent) event);
        }
    }

    private void logUnknownAdapters(AuctionEvent auctionEvent) {
        final BidRequest bidRequest = auctionEvent.getAuctionContext().getBidRequest();
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final ObjectNode analytics = extPrebid != null ? extPrebid.getAnalytics() : null;
        final Iterator<String> analyticsFieldNames = analytics != null && !analytics.isEmpty()
                ? analytics.fieldNames() : null;

        if (analyticsFieldNames != null) {
            final List<String> unknownAdapterNames = StreamUtil.asStream(analyticsFieldNames)
                    .filter(adapter -> !reporterNames.contains(adapter))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(unknownAdapterNames)) {
                final Site site = bidRequest.getSite();
                final String refererUrl = site != null ? site.getPage() : null;
                UNKNOWN_ADAPTERS_LOGGER.warn(
                        String.format("Unknown adapters in ext.prebid.analytics[].adapter: %s, referrer: '%s'",
                                unknownAdapterNames, refererUrl), 0.01);
            }
        }
    }

    private static <T> T updateEvent(T event, String adapter) {
        if (!ADAPTERS_PERMITTED_FOR_FULL_DATA.contains(adapter) && event instanceof AuctionEvent) {
            final AuctionEvent auctionEvent = (AuctionEvent) event;
            final AuctionContext updatedAuctionContext =
                    updateAuctionContextAdapter(auctionEvent.getAuctionContext(), adapter);
            return updatedAuctionContext != null
                    ? (T) auctionEvent.toBuilder().auctionContext(updatedAuctionContext).build()
                    : event;
        }

        return event;
    }

    private static AuctionContext updateAuctionContextAdapter(AuctionContext context, String adapter) {
        final BidRequest updatedBidRequest = updateBidRequest(context.getBidRequest(), adapter);

        return updatedBidRequest != null ? context.toBuilder().bidRequest(updatedBidRequest).build() : null;
    }

    private static BidRequest updateBidRequest(BidRequest bidRequest, String adapterName) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final ObjectNode analytics = extPrebid != null ? extPrebid.getAnalytics() : null;
        ObjectNode preparedAnalytics = null;
        if (analytics != null && !analytics.isEmpty()) {
            preparedAnalytics = prepareAnalytics(analytics, adapterName);
        }
        final ExtRequest updatedExtRequest = preparedAnalytics != null ? ExtRequest.of(extPrebid.toBuilder()
                .analytics(preparedAnalytics)
                .build()) : null;

        if (updatedExtRequest != null) {
            updatedExtRequest.addProperties(requestExt.getProperties());
            return bidRequest.toBuilder().ext(updatedExtRequest).build();
        }

        return null;
    }

    private static ObjectNode prepareAnalytics(ObjectNode analytics, String adapterName) {
        final ObjectNode analyticsNodeCopy = analytics.deepCopy();
        final JsonNode adapterNode = analyticsNodeCopy.get(adapterName);
        analyticsNodeCopy.removeAll();
        if (adapterNode != null) {
            analyticsNodeCopy.set(adapterName, adapterNode);
        }

        return !analyticsNodeCopy.isEmpty() ? analyticsNodeCopy : null;
    }
}
