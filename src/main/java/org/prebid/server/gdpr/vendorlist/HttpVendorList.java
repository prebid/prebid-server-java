package org.prebid.server.gdpr.vendorlist;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.vendorlist.proto.VendorListInfo;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

public class HttpVendorList implements VendorList {

    private static final Logger logger = LoggerFactory.getLogger(HttpVendorList.class);

    private final HttpClient httpClient;
    private final String endpointTemplate;

    public HttpVendorList(HttpClient httpClient, String endpointTemplate) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpointTemplate = Objects.requireNonNull(endpointTemplate);
    }

    @Override
    public Future<VendorListInfo> forVersion(int version, Timeout timeout) {
        final Future<VendorListInfo> future = Future.future();

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            handleException(future, version, new TimeoutException("Timeout has been exceeded"));
        } else {
            httpClient.getAbs(String.format(endpointTemplate, version),
                    response -> handleResponse(response, future, version))
                    .exceptionHandler(throwable -> handleException(future, version, throwable))
                    .setTimeout(remainingTimeout)
                    .end();
        }

        return future;
    }

    private void handleException(Future<VendorListInfo> future, int version, Throwable throwable) {
        fail(future, version, throwable.getMessage());
    }

    private void handleResponse(HttpClientResponse response, Future<VendorListInfo> future, int version) {
        response
                .bodyHandler(buffer -> handleBody(future, version, response.statusCode(), buffer.toString()))
                .exceptionHandler(throwable -> handleException(future, version, throwable));
    }

    private void handleBody(Future<VendorListInfo> future, int version, int statusCode, String body) {
        if (statusCode != 200) {
            fail(future, version, "response code was %d", statusCode);
        } else {
            final VendorListInfo vendorList;
            try {
                vendorList = Json.decodeValue(body, VendorListInfo.class);
            } catch (DecodeException e) {
                fail(future, version, "parsing json failed for response: %s with message: %s", body, e.getMessage());
                return;
            }

            // we should care on obtained vendor list, because it can be cached and never be requested again
            if (isVendorListValid(vendorList)) {
                future.complete(vendorList);
            } else {
                fail(future, version, "fetched vendor list parsed but has invalid data: %s", body);
            }
        }
    }

    private static boolean isVendorListValid(VendorListInfo vendorList) {
        return vendorList.getVendorListVersion() > 0
                && vendorList.getLastUpdated() != null
                && vendorList.getVendors() != null
                && !vendorList.getVendors().isEmpty();
    }

    private static void fail(Future<VendorListInfo> future, int version, String errorMessageFormat, Object... args) {
        final String message = String.format("Error fetching vendor list via HTTP for version %s with error: %s",
                version, String.format(errorMessageFormat, args));

        logger.info(message);
        future.fail(message);
    }
}
