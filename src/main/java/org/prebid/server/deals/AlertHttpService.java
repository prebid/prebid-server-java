package org.prebid.server.deals;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.deals.model.AlertEvent;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.AlertProxyProperties;
import org.prebid.server.deals.model.AlertSource;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AlertHttpService {

    private static final Logger logger = LoggerFactory.getLogger(AlertHttpService.class);
    private static final String RAISE = "RAISE";
    private static final Long DEFAULT_HIGH_ALERT_PERIOD = 15L;

    private final JacksonMapper mapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final AlertProxyProperties alertProxyProperties;
    private final AlertSource alertSource;
    private final boolean enabled;
    private final String url;
    private final long timeoutMillis;
    private final String authHeaderValue;
    private final Map<String, Long> alertTypes;
    private final Map<String, Long> alertTypesCounters;

    public AlertHttpService(JacksonMapper mapper, HttpClient httpClient, Clock clock,
                            DeploymentProperties deploymentProperties,
                            AlertProxyProperties alertProxyProperties) {
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.clock = Objects.requireNonNull(clock);
        this.alertProxyProperties = Objects.requireNonNull(alertProxyProperties);
        this.alertSource = makeSource(Objects.requireNonNull(deploymentProperties));
        this.enabled = alertProxyProperties.isEnabled();
        this.timeoutMillis = TimeUnit.SECONDS.toMillis(alertProxyProperties.getTimeoutSec());
        this.url = HttpUtil.validateUrl(Objects.requireNonNull(alertProxyProperties.getUrl()));
        this.authHeaderValue = HttpUtil.makeBasicAuthHeaderValue(alertProxyProperties.getUsername(),
                alertProxyProperties.getPassword());
        this.alertTypes = new ConcurrentHashMap<>(alertProxyProperties.getAlertTypes());
        this.alertTypesCounters = new ConcurrentHashMap<>(alertTypes.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), s -> 0L)));
    }

    private static AlertSource makeSource(DeploymentProperties deploymentProperties) {
        return AlertSource.builder()
                .env(deploymentProperties.getProfile())
                .region(deploymentProperties.getPbsRegion())
                .dataCenter(deploymentProperties.getDataCenter())
                .subSystem(deploymentProperties.getSubSystem())
                .system(deploymentProperties.getSystem())
                .hostId(deploymentProperties.getPbsHostId())
                .build();
    }

    public void alertWithPeriod(String serviceName, String alertType, AlertPriority alertPriority, String message) {
        if (alertTypes.get(alertType) == null) {
            alertTypes.put(alertType, DEFAULT_HIGH_ALERT_PERIOD);
            alertTypesCounters.put(alertType, 0L);
        }

        long count = alertTypesCounters.get(alertType);
        final long period = alertTypes.get(alertType);

        alertTypesCounters.put(alertType, ++count);
        final String formattedMessage =
                String.format("Service %s failed to send request %s time(s) with error message : %s",
                        serviceName, count, message);
        if (count == 1) {
            alert(alertType, alertPriority, formattedMessage);
        } else if (count % period == 0) {
            alert(alertType, AlertPriority.HIGH, formattedMessage);
        }
    }

    public void resetAlertCount(String alertType) {
        alertTypesCounters.put(alertType, 0L);
    }

    public void alert(String name, AlertPriority alertPriority, String message) {
        if (!enabled) {
            logger.warn("Alert to proxy is not enabled in pbs configuration");
            return;
        }

        final AlertEvent alertEvent = makeEvent(RAISE, alertPriority, name, message, alertSource);

        try {
            httpClient.post(alertProxyProperties.getUrl(), headers(),
                    mapper.encode(Collections.singletonList(alertEvent)), timeoutMillis)
                    .setHandler(this::handleResponse);
        } catch (EncodeException e) {
            logger.warn("Can't parse alert proxy payload: {0}", e.getMessage());
        }
    }

    private AlertEvent makeEvent(String action, AlertPriority priority, String name, String details,
                                 AlertSource alertSource) {
        return AlertEvent.builder()
                .id(UUID.randomUUID().toString())
                .action(action.toUpperCase())
                .priority(priority)
                .name(name)
                .details(details)
                .updatedAt(ZonedDateTime.now(clock))
                .source(alertSource)
                .build();
    }

    private MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.PG_TRX_ID, UUID.randomUUID().toString())
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpUtil.AUTHORIZATION_HEADER, authHeaderValue);
    }

    private void handleResponse(AsyncResult<HttpClientResponse> httpClientResponseResult) {
        if (httpClientResponseResult.failed()) {
            logger.error("Error occurred during sending alert to proxy at {0}::{1} ", url,
                    httpClientResponseResult.cause().getMessage());
        }
    }
}
