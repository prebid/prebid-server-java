package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.net.HttpRequest;
import org.prebid.server.hooks.modules.optable.targeting.model.net.HttpResponse;
import org.prebid.server.hooks.modules.optable.targeting.model.net.OptableCall;
import org.prebid.server.hooks.modules.optable.targeting.model.net.OptableError;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.EndpointResolver;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class APIClient {

    private static final Logger logger = LoggerFactory.getLogger(APIClient.class);

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final String endpoint;

    private final HttpClient httpClient;

    private final double logSamplingRate;

    private final OptableResponseMapper responseMapper;

    public APIClient(String endpoint,
                     HttpClient httpClient,
                     double logSamplingRate,
                     OptableResponseMapper responseMapper) {

        this.endpoint = Objects.requireNonNull(endpoint);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.logSamplingRate = logSamplingRate;
        this.responseMapper = Objects.requireNonNull(responseMapper);
    }

    public Future<TargetingResult> getTargeting(String apiKey, String tenant, String origin,
                                                Query query, List<String> ips, long timeout) {

        final MultiMap headers = HeadersMultiMap.headers()
                .add(HttpUtil.ACCEPT_HEADER, "application/json");

        if (StringUtils.isNotEmpty(apiKey)) {
            headers.add(HttpUtil.AUTHORIZATION_HEADER, "Bearer %s".formatted(apiKey));
        }

        if (CollectionUtils.isNotEmpty(ips)) {
            ips.forEach(ip -> {
                headers.add(HttpUtil.X_FORWARDED_FOR_HEADER, ip);
            });
        }

        final HttpRequest request = HttpRequest.builder()
                .uri(EndpointResolver.resolve(endpoint, tenant, origin))
                .query(query.toQueryString())
                .headers(headers)
                .build();

        return doRequest(request, timeout);
    }

    private Future<TargetingResult> doRequest(HttpRequest httpRequest, long timeout) {
        return createRequest(httpRequest, timeout)
                .compose(response -> processResponse(response, httpRequest))
                .recover(exception -> failResponse(exception, httpRequest))
                .map(this::validateResponse)
                .map(it -> parseSilent(it, httpRequest))
                .recover(exception -> logParsingError(exception, httpRequest));
    }

    private TargetingResult parseSilent(OptableCall optableCall, HttpRequest httpRequest) {
        try {
            return responseMapper.parse(optableCall);
        } catch (Exception e) {
            logParsingError(e, httpRequest);
        }
        return null;
    }

    private Future<TargetingResult> logParsingError(Throwable exception, HttpRequest httpRequest) {
        logger.warn("Error occurred while parsing HTTP response from the Optable url: %s with message: %s"
                .formatted(httpRequest.getUri(), exception.getMessage()), logSamplingRate);
        logger.debug("Error occurred while parsing HTTP response from the Optable url: {}",
                exception, httpRequest.getUri());

        return null;
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
        return Future.succeededFuture(OptableCall.succeededHttp(httpRequest, httpResponse, errorOrNull(statusCode)));
    }

    private static OptableError errorOrNull(int statusCode) {
        if (statusCode != HttpResponseStatus.OK.code() && statusCode != HttpResponseStatus.NO_CONTENT.code()) {
            return OptableError.of(
                    "Unexpected status code: %s.".formatted(statusCode),
                    statusCode == HttpResponseStatus.BAD_REQUEST.code()
                            ? OptableError.Type.BAD_INPUT
                            : OptableError.Type.BAD_SERVER_RESPONSE);
        }
        return null;
    }

    private Future<OptableCall> failResponse(Throwable exception, HttpRequest httpRequest) {
        conditionalLogger.warn("Error occurred while sending HTTP request to the Optable url: %s with message: %s"
                .formatted(httpRequest.getUri(), exception.getMessage()), logSamplingRate);
        logger.debug("Error occurred while sending HTTP request to the Optable url: {}",
                exception, httpRequest.getUri());

        final OptableError.Type errorType =
                exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                        ? OptableError.Type.TIMEOUT
                        : OptableError.Type.GENERIC;

        return Future.succeededFuture(
                OptableCall.failedHttp(httpRequest, OptableError.of(exception.getMessage(), errorType)));
    }

    private OptableCall validateResponse(OptableCall response) {
        return Optional.ofNullable(response)
                .map(OptableCall::getResponse)
                .map(resp -> {
                    if (resp.getStatusCode() != HttpResponseStatus.OK.code()) {
                        conditionalLogger.warn(("Error occurred while sending HTTP request to the "
                                + "Optable url: %s with message: %s").formatted(response.getRequest().getUri(),
                                resp.getBody()), logSamplingRate);
                        logger.debug("Error occurred while sending HTTP request to the Optable url: {}");

                        return null;
                    }

                    return response;
                })
                .orElse(null);
    }
}
