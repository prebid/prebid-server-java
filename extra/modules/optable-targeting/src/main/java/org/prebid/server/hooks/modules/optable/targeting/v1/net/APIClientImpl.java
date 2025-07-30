package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.ValidationException;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.List;
import java.util.Objects;

public class APIClientImpl implements APIClient {

    private static final Logger logger = LoggerFactory.getLogger(APIClientImpl.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final String TENANT = "{TENANT}";
    private static final String ORIGIN = "{ORIGIN}";

    private final String endpoint;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;
    private final double logSamplingRate;

    public APIClientImpl(String endpoint,
                         HttpClient httpClient,
                         JacksonMapper mapper,
                         double logSamplingRate) {

        this.endpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.logSamplingRate = logSamplingRate;
    }

    public Future<TargetingResult> getTargeting(OptableTargetingProperties properties,
                                                Query query,
                                                List<String> ips,
                                                Timeout timeout) {

        final String uri = resolveEndpoint(properties.getTenant(), properties.getOrigin());
        final String queryAsString = query.toQueryString();
        final MultiMap headers = headers(properties, ips);

        return httpClient.get(uri + queryAsString, headers, timeout.remaining())
                .compose(this::validateResponse)
                .map(this::parseResponse)
                .onFailure(exception -> logError(exception, uri));
    }

    private String resolveEndpoint(String tenant, String origin) {
        return endpoint
                .replace(TENANT, tenant)
                .replace(ORIGIN, origin);
    }

    private static MultiMap headers(OptableTargetingProperties properties, List<String> ips) {
        final MultiMap headers = HeadersMultiMap.headers()
                .add(HttpUtil.ACCEPT_HEADER, "application/json");

        final String apiKey = properties.getApiKey();
        if (StringUtils.isNotEmpty(apiKey)) {
            headers.add(HttpUtil.AUTHORIZATION_HEADER, "Bearer %s".formatted(apiKey));
        }

        CollectionUtils.emptyIfNull(ips)
                .forEach(ip -> headers.add(HttpUtil.X_FORWARDED_FOR_HEADER, ip));

        return headers;
    }

    private Future<HttpClientResponse> validateResponse(HttpClientResponse response) {
        if (response.getStatusCode() != HttpResponseStatus.OK.code()) {
            return Future.failedFuture(new ValidationException("Invalid status code: %d", response.getStatusCode()));
        }

        if (StringUtils.isBlank(response.getBody())) {
            return Future.failedFuture(new ValidationException("Empty body"));
        }

        return Future.succeededFuture(response);
    }

    private TargetingResult parseResponse(HttpClientResponse httpResponse) {
        return mapper.decodeValue(httpResponse.getBody(), TargetingResult.class);
    }

    private void logError(Throwable exception, String url) {
        final String errorPrefix = "Error occurred while sending HTTP request to the Optable url:";

        final String error = errorPrefix + " %s with message: %s".formatted(url, exception.getMessage());
        conditionalLogger.warn(error, logSamplingRate);

        logger.debug(errorPrefix + " {}", exception, url);
    }
}
