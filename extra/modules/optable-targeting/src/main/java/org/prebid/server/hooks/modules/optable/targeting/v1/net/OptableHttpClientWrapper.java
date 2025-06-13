package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.JksOptions;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.vertx.httpclient.BasicHttpClient;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.util.concurrent.TimeUnit;

public class OptableHttpClientWrapper {

    private HttpClient httpClient;

    public OptableHttpClientWrapper(Vertx vertx, HttpClientProperties httpClientProperties) {
        this.httpClient = createBasicHttpClient(vertx, httpClientProperties);
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    private static HttpClient createBasicHttpClient(Vertx vertx, HttpClientProperties httpClientProperties) {
        final HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(httpClientProperties.getMaxPoolSize())
                .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
                .setIdleTimeout(httpClientProperties.getIdleTimeoutMs())
                .setPoolCleanerPeriod(httpClientProperties.getPoolCleanerPeriodMs())
                .setTryUseCompression(httpClientProperties.getUseCompression())
                .setConnectTimeout(httpClientProperties.getConnectTimeoutMs())
                // Vert.x's HttpClientRequest needs this value to be 2 for redirections to be followed once,
                // 3 for twice, and so on
                .setMaxRedirects(httpClientProperties.getMaxRedirects() + 1);

        if (httpClientProperties.getSsl()) {
            final JksOptions jksOptions = new JksOptions()
                    .setPath(httpClientProperties.getJksPath())
                    .setPassword(httpClientProperties.getJksPassword());

            options
                    .setSsl(true)
                    .setKeyStoreOptions(jksOptions);
        }

        return new BasicHttpClient(vertx, vertx.createHttpClient(options));
    }
}
