package org.prebid.server.analytics.reporter.pubstack;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.pubstack.model.EventType;
import org.prebid.server.analytics.reporter.pubstack.model.PubstackAnalyticsProperties;
import org.prebid.server.analytics.reporter.pubstack.model.PubstackConfig;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PubstackAnalyticsReporter implements AnalyticsReporter, Initializable {

    private static final Logger logger = LoggerFactory.getLogger(PubstackAnalyticsReporter.class);

    private static final String EVENT_REPORT_ENDPOINT_PATH = "/intake";
    private static final String CONFIG_URL_SUFFIX = "/bootstrap?scopeId=";

    private final long configurationRefreshDelay;
    private final long timeout;
    private final HttpClient httpClient;
    private final JacksonMapper jacksonMapper;
    private final Vertx vertx;

    private final Map<EventType, PubstackEventHandler> eventHandlers;
    private PubstackConfig pubstackConfig;

    public PubstackAnalyticsReporter(PubstackAnalyticsProperties pubstackAnalyticsProperties,
                                     HttpClient httpClient,
                                     JacksonMapper jacksonMapper,
                                     Vertx vertx) {

        this.configurationRefreshDelay =
                Objects.requireNonNull(pubstackAnalyticsProperties.getConfigurationRefreshDelayMs());
        this.timeout = Objects.requireNonNull(pubstackAnalyticsProperties.getTimeoutMs());
        this.httpClient = Objects.requireNonNull(httpClient);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.vertx = Objects.requireNonNull(vertx);

        this.eventHandlers = createEventHandlers(pubstackAnalyticsProperties, httpClient, jacksonMapper, vertx);
        this.pubstackConfig = PubstackConfig.of(pubstackAnalyticsProperties.getScopeId(),
                pubstackAnalyticsProperties.getEndpoint(), Collections.emptyMap());
    }

    private static Map<EventType, PubstackEventHandler> createEventHandlers(
            PubstackAnalyticsProperties pubstackAnalyticsProperties,
            HttpClient httpClient,
            JacksonMapper jacksonMapper,
            Vertx vertx) {

        return Arrays.stream(EventType.values())
                .collect(Collectors.toMap(Function.identity(),
                        eventType -> new PubstackEventHandler(
                                pubstackAnalyticsProperties,
                                false,
                                buildEventEndpointUrl(pubstackAnalyticsProperties.getEndpoint(), eventType),
                                jacksonMapper,
                                httpClient,
                                vertx)));
    }

    private static String buildEventEndpointUrl(String endpoint, EventType eventType) {
        return HttpUtil.validateUrl(endpoint + EVENT_REPORT_ENDPOINT_PATH + eventType.name());
    }

    @Override
    public <T> Future<Void> processEvent(T event) {
        final EventType eventType;

        if (event instanceof AmpEvent) {
            eventType = EventType.amp;
        } else if (event instanceof AuctionEvent) {
            eventType = EventType.auction;
        } else if (event instanceof CookieSyncEvent) {
            eventType = EventType.cookiesync;
        } else if (event instanceof NotificationEvent) {
            eventType = EventType.notification;
        } else if (event instanceof SetuidEvent) {
            eventType = EventType.setuid;
        } else if (event instanceof VideoEvent) {
            eventType = EventType.video;
        } else {
            eventType = null;
        }

        if (eventType != null) {
            eventHandlers.get(eventType).handle(event);
        }

        return Future.succeededFuture();
    }

    @Override
    public int vendorId() {
        return 0;
    }

    @Override
    public String name() {
        return "pubstack";
    }

    @Override
    public void initialize() {
        vertx.setPeriodic(configurationRefreshDelay, id -> fetchRemoteConfig());
        fetchRemoteConfig();
    }

    void shutdown() {
        eventHandlers.values().forEach(PubstackEventHandler::reportEvents);
    }

    private void fetchRemoteConfig() {
        logger.info("[pubstack] Updating config: {0}", pubstackConfig);
        httpClient.get(makeEventEndpointUrl(pubstackConfig.getEndpoint(), pubstackConfig.getScopeId()), timeout)
                .map(this::processRemoteConfigurationResponse)
                .onComplete(this::updateConfigsOnChange);
    }

    private PubstackConfig processRemoteConfigurationResponse(HttpClientResponse response) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException(String.format("[pubstack] Failed to fetch config, reason: HTTP status code %d",
                    statusCode));
        }
        final String body = response.getBody();
        try {
            return jacksonMapper.decodeValue(body, PubstackConfig.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("[pubstack] Failed to fetch config, reason: failed to parse"
                    + " response: %s", body), e);
        }
    }

    private void updateConfigsOnChange(AsyncResult<PubstackConfig> asyncConfigResult) {
        if (asyncConfigResult.failed()) {
            logger.error("[pubstask] Fail to fetch remote configuration: {0}", asyncConfigResult.cause().getMessage());
        } else if (!Objects.equals(pubstackConfig, asyncConfigResult.result())) {
            final PubstackConfig pubstackConfig = asyncConfigResult.result();
            eventHandlers.values().forEach(PubstackEventHandler::reportEvents);
            this.pubstackConfig = pubstackConfig;
            updateHandlers(pubstackConfig);
        }
    }

    private void updateHandlers(PubstackConfig pubstackConfig) {
        final Map<EventType, Boolean> handlersEnabled = MapUtils.emptyIfNull(pubstackConfig.getFeatures());

        eventHandlers.forEach((eventType, eventHandler) -> eventHandler.updateConfig(
                BooleanUtils.toBooleanDefaultIfNull(handlersEnabled.get(eventType), false),
                makeEventHandlerEndpoint(pubstackConfig.getEndpoint(), eventType),
                pubstackConfig.getScopeId()));
    }

    private static String makeEventEndpointUrl(String endpoint, String scopeId) {
        try {
            return HttpUtil.validateUrl(endpoint + CONFIG_URL_SUFFIX + scopeId);
        } catch (IllegalArgumentException e) {
            final String message = String.format("[pubstack] Failed to create remote config server url for endpoint:"
                    + " %s", endpoint);
            logger.error(message);
            throw new PreBidException(message);
        }
    }

    private String makeEventHandlerEndpoint(String endpoint, EventType eventType) {
        try {
            return HttpUtil.validateUrl(endpoint + EVENT_REPORT_ENDPOINT_PATH + "/" + eventType.name());
        } catch (IllegalArgumentException e) {
            final String message = String.format("[pubstack] Failed to create event report url for endpoint: %s",
                    endpoint);
            logger.error(message);
            throw new PreBidException(message);
        }
    }
}
