package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.net.HttpRequest;
import org.prebid.server.hooks.modules.optable.targeting.model.net.HttpResponse;
import org.prebid.server.hooks.modules.optable.targeting.model.net.OptableCall;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class APIClientImpl implements APIClient {

    private static final String TENANT = "{TENANT}";
    private static final String ORIGIN = "{ORIGIN}";
    private static final Logger logger = LoggerFactory.getLogger(APIClientImpl.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);
    private final String endpoint;
    private final HttpClient httpClient;
    private final double logSamplingRate;
    private final OptableResponseMapper responseMapper;

    public APIClientImpl(String endpoint,
                         HttpClient httpClient,
                         double logSamplingRate,
                         OptableResponseMapper responseMapper) {

        this.endpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.httpClient = Objects.requireNonNull(httpClient);
        this.logSamplingRate = logSamplingRate;
        this.responseMapper = Objects.requireNonNull(responseMapper);
    }

    public Future<TargetingResult> getTargeting(OptableTargetingProperties properties,
                                                Query query, List<String> ips, long timeout) {

        final MultiMap headers = HeadersMultiMap.headers()
                .add(HttpUtil.ACCEPT_HEADER, "application/json");

        final String apiKey = properties.getApiKey();
        if (StringUtils.isNotEmpty(apiKey)) {
            headers.add(HttpUtil.AUTHORIZATION_HEADER, "Bearer %s".formatted(apiKey));
        }

        if (CollectionUtils.isNotEmpty(ips)) {
            ips.forEach(ip -> {
                headers.add(HttpUtil.X_FORWARDED_FOR_HEADER, ip);
            });
        }

        final HttpRequest request = HttpRequest.builder()
                .uri(resolveEndpoint(endpoint, properties.getTenant(), properties.getOrigin()))
                .query(query.toQueryString())
                .headers(headers)
                .build();

        return doRequest(request, timeout);
    }

    private String resolveEndpoint(String endpointTemplate, String tenant, String origin) {
        return endpointTemplate.replace(TENANT, tenant)
                .replace(ORIGIN, origin);
    }

    private Future<TargetingResult> doRequest(HttpRequest httpRequest, long timeout) {
        return createRequest(httpRequest, timeout)
                .compose(response -> processResponse(response, httpRequest))
                .recover(exception -> failResponse(exception, httpRequest))
                .compose(this::validateResponse)
                .compose(it -> parseSilent(it, httpRequest))
                .recover(exception -> logParsingError(exception, httpRequest));
    }

    private Future<TargetingResult> parseSilent(OptableCall optableCall, HttpRequest httpRequest) {
        try {
            final TargetingResult result = responseMapper.parse(optableCall);
            return result != null
                    ? Future.succeededFuture(result)
                    : Future.failedFuture("Can't parse Optable response");
        } catch (Exception e) {
            logParsingError(e, httpRequest);
        }
        return Future.failedFuture("Can't parse Optable response");
    }

    private Future<TargetingResult> logParsingError(Throwable exception, HttpRequest httpRequest) {
        final String error = "Error occurred while parsing HTTP response from the Optable url: %s with message: %s"
                .formatted(httpRequest.getUri(), exception.getMessage());
        logger.warn(error, logSamplingRate);
        logger.debug("Error occurred while parsing HTTP response from the Optable url: {}",
                exception, httpRequest.getUri());

        return Future.failedFuture(error);
    }

    private Future<HttpClientResponse> createRequest(HttpRequest httpRequest, long remainingTimeout) {
        try {
            return httpClient.request(HttpMethod.GET,
                    httpRequest.getUri() + "&id=" + httpRequest.getQuery(),
                    httpRequest.getHeaders(),
                    (String) null,
                    remainingTimeout);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    private static Future<OptableCall> processResponse(HttpClientResponse response,
                                                             HttpRequest httpRequest) {

        final int statusCode = response.getStatusCode();
        final HttpResponse httpResponse = HttpResponse.of(statusCode, response.getHeaders(), response.getBody());
        return Future.succeededFuture(OptableCall.succeededHttp(httpRequest, httpResponse));
    }

    private Future<OptableCall> failResponse(Throwable exception, HttpRequest httpRequest) {
        final String error = "Error occurred while sending HTTP request to the Optable url: %s with message: %s"
                .formatted(httpRequest.getUri(), exception.getMessage());
        conditionalLogger.warn(error, logSamplingRate);
        logger.debug("Error occurred while sending HTTP request to the Optable url: {}",
                exception, httpRequest.getUri());

        return Future.failedFuture(error);
    }

    private Future<OptableCall> validateResponse(OptableCall response) {
        return Optional.ofNullable(response)
                .map(OptableCall::getResponse)
                .map(resp -> {
                    if (resp.getStatusCode() != HttpResponseStatus.OK.code()) {
                        final String error = "Error occurred while sending HTTP request to the "
                                + "Optable url: %s with message: %s".formatted(response.getRequest().getUri(),
                                resp.getBody());
                        conditionalLogger.warn(error, logSamplingRate);
                        logger.debug("Error occurred while sending HTTP request to the Optable url: {}");

                        return Future.<OptableCall>failedFuture(error);
                    }

                    return Future.succeededFuture(response);
                })
                .orElse(Future.<OptableCall>failedFuture("OptableCall validation error"));
    }
}
