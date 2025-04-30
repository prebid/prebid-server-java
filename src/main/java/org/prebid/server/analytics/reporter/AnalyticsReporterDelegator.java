package org.prebid.server.analytics.reporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityInvocationPayload;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.privacy.enforcement.TcfEnforcement;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.util.StreamUtil;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class AnalyticsReporterDelegator {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsReporterDelegator.class);
    private static final ConditionalLogger unknownAdaptersLogger = new ConditionalLogger(logger);
    private static final Set<String> ADAPTERS_PERMITTED_FOR_FULL_DATA = Collections.singleton("logAnalytics");

    private final Vertx vertx;
    private final List<AnalyticsReporter> delegates;
    private final TcfEnforcement tcfEnforcement;
    private final UserFpdActivityMask mask;
    private final Metrics metrics;
    private final double logSamplingRate;
    private final Set<String> globalEnabledAdapters;
    private final JacksonMapper mapper;

    private final Set<Integer> reporterVendorIds;
    private final Set<String> reporterNames;

    public AnalyticsReporterDelegator(Vertx vertx,
                                      List<AnalyticsReporter> delegates,
                                      TcfEnforcement tcfEnforcement,
                                      UserFpdActivityMask userFpdActivityMask,
                                      Metrics metrics,
                                      double logSamplingRate,
                                      Set<String> globalEnabledAdapters,
                                      JacksonMapper mapper) {

        this.vertx = Objects.requireNonNull(vertx);
        this.delegates = Objects.requireNonNull(delegates);
        this.tcfEnforcement = Objects.requireNonNull(tcfEnforcement);
        this.mask = Objects.requireNonNull(userFpdActivityMask);
        this.metrics = Objects.requireNonNull(metrics);
        this.logSamplingRate = logSamplingRate;
        this.globalEnabledAdapters = CollectionUtils.isEmpty(globalEnabledAdapters)
                ? Collections.emptySet()
                : globalEnabledAdapters;
        this.mapper = Objects.requireNonNull(mapper);

        reporterVendorIds = delegates.stream().map(AnalyticsReporter::vendorId).collect(Collectors.toSet());
        reporterNames = delegates.stream().map(AnalyticsReporter::name).collect(Collectors.toSet());
    }

    public <T> void processEvent(T event) {
        for (AnalyticsReporter analyticsReporter : delegates) {
            if (!isAllowedAdapter(event, analyticsReporter.name())) {
                continue;
            }

            vertx.runOnContext(ignored -> processEventByReporter(analyticsReporter, event));
        }
    }

    public <T> void processEvent(T event, TcfContext tcfContext) {
        tcfEnforcement.enforce(reporterVendorIds, tcfContext)
                .onComplete(privacyEnforcementMap -> delegateEvent(event, tcfContext, privacyEnforcementMap));
    }

    private <T> void delegateEvent(T event,
                                   TcfContext tcfContext,
                                   AsyncResult<Map<Integer, PrivacyEnforcementAction>> privacyEnforcementMapResult) {

        if (privacyEnforcementMapResult.succeeded()) {
            final Map<Integer, PrivacyEnforcementAction> privacyEnforcementActionMap =
                    privacyEnforcementMapResult.result();
            checkUnknownAdaptersForAuctionEvent(event);
            for (AnalyticsReporter analyticsReporter : delegates) {
                final String name = analyticsReporter.name();
                if (!isAllowedAdapter(event, name)) {
                    continue;
                }

                final T updatedEvent = updateEvent(event, name);
                final int reporterVendorId = analyticsReporter.vendorId();
                // resultForVendorIds is guaranteed returning for each provided value except null,
                // but to be sure lets use getOrDefault
                final PrivacyEnforcementAction reporterPrivacyAction = privacyEnforcementActionMap
                        .getOrDefault(reporterVendorId, PrivacyEnforcementAction.restrictAll());
                if (!reporterPrivacyAction.isBlockAnalyticsReport()) {
                    vertx.runOnContext(ignored -> processEventByReporter(analyticsReporter, updatedEvent));
                }
            }
        } else {
            final Throwable privacyEnforcementException = privacyEnforcementMapResult.cause();
            logger.error("Analytics TCF enforcement check failed for consentString: {} and "
                            + "delegates with vendorIds {}", privacyEnforcementException,
                    tcfContext.getConsentString(), delegates);
        }
    }

    private <T> void checkUnknownAdaptersForAuctionEvent(T event) {
        if (event instanceof AuctionEvent) {
            logUnknownAdapters((AuctionEvent) event);
        }
    }

    private void logUnknownAdapters(AuctionEvent auctionEvent) {
        final AuctionContext auctionContext = auctionEvent.getAuctionContext();
        final BidRequest bidRequest = auctionContext != null ? auctionContext.getBidRequest() : null;
        final ExtRequest extRequest = bidRequest != null ? bidRequest.getExt() : null;
        final ExtRequestPrebid extPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final JsonNode analytics = extPrebid != null ? extPrebid.getAnalytics() : null;
        final Iterator<String> analyticsFieldNames = isNotEmptyObjectNode(analytics) ? analytics.fieldNames() : null;

        if (analyticsFieldNames != null) {
            final List<String> unknownAdapterNames = StreamUtil.asStream(analyticsFieldNames)
                    .filter(adapter -> !reporterNames.contains(adapter))
                    .toList();
            if (CollectionUtils.isNotEmpty(unknownAdapterNames)) {
                final Site site = bidRequest.getSite();
                final String refererUrl = site != null ? site.getPage() : null;
                unknownAdaptersLogger.warn("Unknown adapters in ext.prebid.analytics[].adapter: %s, referrer: '%s'"
                        .formatted(unknownAdapterNames, refererUrl), logSamplingRate);
            }
        }
    }

    private static boolean isNotEmptyObjectNode(JsonNode analytics) {
        return analytics != null && analytics.isObject() && !analytics.isEmpty();
    }

    private <T> boolean isAllowedAdapter(T event, String adapter) {
        final ActivityInfrastructure activityInfrastructure;
        final ActivityInvocationPayload activityInvocationPayload;
        switch (event) {
            case AuctionEvent auctionEvent -> {
                if (isNotAllowedAdapterByGlobalOrAccountAnalyticsConfig(adapter, auctionEvent.getAuctionContext())) {
                    return false;
                }
                final AuctionContext auctionContext = auctionEvent.getAuctionContext();
                activityInfrastructure = auctionContext != null ? auctionContext.getActivityInfrastructure() : null;
                activityInvocationPayload = auctionContext != null
                        ? BidRequestActivityInvocationPayload.of(
                        activityInvocationPayload(adapter),
                        auctionContext.getBidRequest())
                        : null;
            }
            case AmpEvent ampEvent -> {
                if (isNotAllowedAdapterByGlobalOrAccountAnalyticsConfig(adapter, ampEvent.getAuctionContext())) {
                    return false;
                }

                final AuctionContext auctionContext = ampEvent.getAuctionContext();
                activityInfrastructure = auctionContext != null ? auctionContext.getActivityInfrastructure() : null;
                activityInvocationPayload = auctionContext != null
                        ? BidRequestActivityInvocationPayload.of(
                        activityInvocationPayload(adapter),
                        auctionContext.getBidRequest())
                        : null;
            }
            case NotificationEvent notificationEvent -> {
                if (isNotAllowedAdapterByGlobalOrAccountAnalyticsConfig(adapter, notificationEvent.getAccount())) {
                    return false;
                }
                activityInfrastructure = notificationEvent.getActivityInfrastructure();
                activityInvocationPayload = activityInvocationPayload(adapter);
            }
            case VideoEvent videoEvent -> {
                if (isNotAllowedAdapterByGlobalOrAccountAnalyticsConfig(adapter, videoEvent.getAuctionContext())) {
                    return false;
                }
                activityInfrastructure = null;
                activityInvocationPayload = null;
            }
            case null, default -> {
                activityInfrastructure = null;
                activityInvocationPayload = null;
            }
        }

        return isAllowedActivity(activityInfrastructure, Activity.REPORT_ANALYTICS, activityInvocationPayload);
    }

    private boolean isNotAllowedAdapterByGlobalOrAccountAnalyticsConfig(String adapter, AuctionContext auctionContext) {
        return isNotAllowedAdapterByGlobalOrAccountAnalyticsConfig(adapter,
                Optional.ofNullable(auctionContext)
                        .map(AuctionContext::getAccount)
                        .orElse(null));
    }

    private boolean isNotAllowedAdapterByGlobalOrAccountAnalyticsConfig(String adapter, Account account) {
        final Map<String, ObjectNode> modules = Optional.ofNullable(account)
                .map(Account::getAnalytics)
                .map(AccountAnalyticsConfig::getModules)
                .orElse(null);

        if (modules != null && modules.containsKey(adapter)) {
            final ObjectNode moduleConfig = modules.get(adapter);

            if (moduleConfig == null || !moduleConfig.has("enabled")) {
                return false;
            }

            return !moduleConfig.get("enabled").asBoolean();
        }

        return !globalEnabledAdapters.contains(adapter);
    }

    private static ActivityInvocationPayload activityInvocationPayload(String adapterName) {
        return ActivityInvocationPayloadImpl.of(ComponentType.ANALYTICS, adapterName);
    }

    private <T> T updateEvent(T event, String adapter) {
        if (!ADAPTERS_PERMITTED_FOR_FULL_DATA.contains(adapter) && event instanceof AuctionEvent auctionEvent) {
            final AuctionContext updatedAuctionContext =
                    updateAuctionContextAdapter(auctionEvent.getAuctionContext(), adapter);
            return updatedAuctionContext != null
                    ? (T) auctionEvent.toBuilder().auctionContext(updatedAuctionContext).build()
                    : event;
        }

        return event;
    }

    private AuctionContext updateAuctionContextAdapter(AuctionContext context, String adapter) {
        if (context == null) {
            return null;
        }

        final BidRequest bidRequest = context.getBidRequest();
        final ActivityInfrastructure activityInfrastructure = context.getActivityInfrastructure();
        final BidRequest updatedBidRequest = updateBidRequest(bidRequest, adapter, activityInfrastructure);

        return updatedBidRequest != null
                ? context.toBuilder()
                .bidRequest(updatedBidRequest)
                .build()
                : null;
    }

    private BidRequest updateBidRequest(BidRequest bidRequest,
                                        String adapter,
                                        ActivityInfrastructure infrastructure) {

        final ActivityInvocationPayload payload = BidRequestActivityInvocationPayload.of(
                activityInvocationPayload(adapter),
                bidRequest);

        final boolean disallowTransmitUfpd = !isAllowedActivity(infrastructure, Activity.TRANSMIT_UFPD, payload);
        final boolean disallowTransmitEids = !isAllowedActivity(infrastructure, Activity.TRANSMIT_EIDS, payload);
        final boolean disallowTransmitGeo = !isAllowedActivity(infrastructure, Activity.TRANSMIT_GEO, payload);

        final User user = bidRequest != null ? bidRequest.getUser() : null;
        final User resolvedUser = mask.maskUser(user, disallowTransmitUfpd, disallowTransmitEids);

        final Device device = bidRequest != null ? bidRequest.getDevice() : null;
        final Device resolvedDevice = mask.maskDevice(device, disallowTransmitUfpd, disallowTransmitGeo);

        final ExtRequest requestExt = bidRequest != null ? bidRequest.getExt() : null;
        final ExtRequest updatedExtRequest = updateExtRequest(requestExt, adapter);

        return resolvedUser != null || resolvedDevice != null || updatedExtRequest != null
                ? bidRequest.toBuilder()
                .user(resolvedUser != null ? resolvedUser : user)
                .device(resolvedDevice != null ? resolvedDevice : device)
                .ext(updatedExtRequest != null ? updatedExtRequest : requestExt)
                .build()
                : null;
    }

    private static boolean isAllowedActivity(ActivityInfrastructure activityInfrastructure,
                                             Activity activity,
                                             ActivityInvocationPayload activityInvocationPayload) {

        return activityInfrastructure != null
                ? activityInfrastructure.isAllowed(activity, activityInvocationPayload)
                : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static ExtRequest updateExtRequest(ExtRequest requestExt, String adapterName) {
        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final JsonNode analytics = extPrebid != null ? extPrebid.getAnalytics() : null;
        final ObjectNode preparedAnalytics = isNotEmptyObjectNode(analytics)
                ? prepareAnalytics((ObjectNode) analytics, adapterName)
                : null;
        final ExtRequest updatedExtRequest = preparedAnalytics != null
                ? ExtRequest.of(extPrebid.toBuilder().analytics(preparedAnalytics).build())
                : null;

        if (updatedExtRequest != null) {
            updatedExtRequest.addProperties(requestExt.getProperties());
        }

        return updatedExtRequest;
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

    private <T> void processEventByReporter(AnalyticsReporter analyticsReporter, T event) {
        final String reporterName = analyticsReporter.name();

        analyticsReporter.processEvent(updateEventIfRequired(event, analyticsReporter.name()))
                .map(ignored -> processSuccess(event, reporterName))
                .otherwise(exception -> processFail(exception, event, reporterName));
    }

    private <T> Future<Void> processSuccess(T event, String reporterName) {
        updateMetricsByEventType(event, reporterName, MetricName.ok);
        return Future.succeededFuture();
    }

    private <T> Future<Void> processFail(Throwable exception, T event, String reporterName) {
        final MetricName failedResult;
        if (exception instanceof TimeoutException || exception instanceof ConnectTimeoutException) {
            failedResult = MetricName.timeout;
        } else if (exception instanceof InvalidRequestException) {
            failedResult = MetricName.badinput;
        } else {
            failedResult = MetricName.err;
        }
        updateMetricsByEventType(event, reporterName, failedResult);
        return Future.failedFuture(exception);
    }

    private <T> void updateMetricsByEventType(T event, String analyticsCode, MetricName result) {
        final MetricName eventType = switch (event) {
            case AmpEvent ampEvent -> MetricName.event_amp;
            case AuctionEvent auctionEvent -> MetricName.event_auction;
            case CookieSyncEvent cookieSyncEvent -> MetricName.event_cookie_sync;
            case NotificationEvent notificationEvent -> MetricName.event_notification;
            case SetuidEvent setuidEvent -> MetricName.event_setuid;
            case VideoEvent videoEvent -> MetricName.event_video;
            case null, default -> MetricName.event_unknown;
        };

        metrics.updateAnalyticEventMetric(analyticsCode, eventType, result);
    }

    private <T> T updateEventIfRequired(T event, String adapter) {
        switch (event) {
            case AuctionEvent auctionEvent -> {
                final AuctionContext auctionContext = updateAuctionContext(auctionEvent.getAuctionContext(), adapter);
                return auctionContext != null
                        ? (T) auctionEvent.toBuilder().auctionContext(auctionContext).build()
                        : event;
            }
            case AmpEvent ampEvent -> {
                final AuctionContext auctionContext = updateAuctionContext(ampEvent.getAuctionContext(), adapter);
                return auctionContext != null
                        ? (T) ampEvent.toBuilder().auctionContext(auctionContext).build()
                        : event;
            }
            case VideoEvent videoEvent -> {
                final AuctionContext auctionContext = updateAuctionContext(videoEvent.getAuctionContext(), adapter);
                return auctionContext != null
                        ? (T) videoEvent.toBuilder().auctionContext(auctionContext).build()
                        : event;
            }
            case null, default -> {
                return event;
            }
        }
    }

    private AuctionContext updateAuctionContext(AuctionContext context, String adapterName) {
        final Map<String, ObjectNode> modules = Optional.ofNullable(context)
                .map(AuctionContext::getAccount)
                .map(Account::getAnalytics)
                .map(AccountAnalyticsConfig::getModules)
                .orElse(null);

        if (modules != null && modules.containsKey(adapterName)) {
            final ObjectNode moduleConfig = modules.get(adapterName);
            if (moduleConfigContainsAdapterSpecificData(moduleConfig)) {
                final ExtRequestPrebid extRequestPrebid = Optional.ofNullable(context.getBidRequest())
                        .map(BidRequest::getExt)
                        .map(ExtRequest::getPrebid)
                        .orElse(null);

                final JsonNode analyticsNode = extRequestPrebid != null ? extRequestPrebid.getAnalytics() : null;

                if (analyticsNode != null && analyticsNode.isObject()) {
                    final ObjectNode adapterNode = Optional.ofNullable((ObjectNode) analyticsNode.get(adapterName))
                            .orElse(mapper.mapper().createObjectNode());

                    moduleConfig.fields().forEachRemaining(entry -> {
                        final String fieldName = entry.getKey();
                        if (!"enabled".equals(fieldName) && !adapterNode.has(fieldName)) {
                            adapterNode.set(fieldName, entry.getValue());
                        }
                    });

                    ((ObjectNode) analyticsNode).set(adapterName, adapterNode);
                    final ExtRequestPrebid updatedPrebid = extRequestPrebid.toBuilder()
                            .analytics(analyticsNode)
                            .build();
                    final ExtRequest updatedExtRequest = ExtRequest.of(updatedPrebid);
                    final BidRequest updatedBidRequest = context.getBidRequest().toBuilder()
                            .ext(updatedExtRequest)
                            .build();
                    return context.toBuilder()
                            .bidRequest(updatedBidRequest)
                            .build();
                }
            }
        }

        return null;
    }

    private boolean moduleConfigContainsAdapterSpecificData(ObjectNode moduleConfig) {
        if (moduleConfig != null) {
            final Iterator<String> fieldNames = moduleConfig.fieldNames();
            while (fieldNames.hasNext()) {
                final String fieldName = fieldNames.next();
                if (!"enabled".equals(fieldName)) {
                    return true;
                }
            }
        }

        return false;
    }
}
