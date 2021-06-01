package org.prebid.server.execution;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.log.ConditionalLoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class HttpResponseSender {

    private HttpResponseStatus status;

    private Map<CharSequence, CharSequence> headers;

    private String body;

    private Buffer bodyAsBuffer; // alternative body

    private Handler<Throwable> exceptionHandler;

    private RoutingContext routingContext;

    private Logger logger;

    private HttpResponseSender() {
    }

    public static HttpResponseSender from(RoutingContext routingContext, Logger logger) {
        final HttpResponseSender responder = new HttpResponseSender();

        responder.routingContext = routingContext;
        responder.logger = logger;

        return responder;
    }

    public HttpResponseSender exceptionHandler(Handler<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    /**
     * Sets HTTP response status, default is {@link HttpResponseStatus#OK} if not set.
     */
    public HttpResponseSender status(HttpResponseStatus status) {
        this.status = status;
        return this;
    }

    public HttpResponseSender headers(Map<CharSequence, CharSequence> headers) {
        this.headers = headers;
        return this;
    }

    public HttpResponseSender addHeader(CharSequence name, CharSequence value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put(name, value);
        return this;
    }

    public HttpResponseSender body(String body) {
        this.body = body;
        return this;
    }

    public HttpResponseSender body(Buffer body) {
        this.bodyAsBuffer = body;
        return this;
    }

    /**
     * Sends HTTP response to the client.
     * <p>
     * Returns true if response was sent, otherwise false.
     */
    public boolean send() {
        if (body != null) {
            return respondWith(body, routingContext.response()::end);
        }

        if (bodyAsBuffer != null) {
            return respondWith(bodyAsBuffer, routingContext.response()::end);
        }

        return respondWith(null, null);
    }

    /**
     * Sends file response to the client.
     */
    public void sendFile(String filename) {
        respondWith(filename, routingContext.response()::sendFile);
    }

    private <T> boolean respondWith(T data, Consumer<T> responseSender) {
        if (connectionClosed()) {
            return false;
        }

        final HttpServerResponse response = routingContext.response();

        if (exceptionHandler != null) {
            response.exceptionHandler(exceptionHandler);
        }

        if (status != null) {
            response.setStatusCode(status.code());
            response.setStatusMessage(status.reasonPhrase());
        }

        if (MapUtils.isNotEmpty(headers)) {
            headers.forEach(response::putHeader);
        }

        if (data != null) {
            responseSender.accept(data);
        } else {
            response.end();
        }
        return true;
    }

    /**
     * Return true if client has closed underlying connection, otherwise false.
     */
    private boolean connectionClosed() {
        final boolean connectionClosed = routingContext.response().closed();

        if (connectionClosed) {
            ConditionalLoggerFactory.getOrCreate(logger)
                    .warn("The client already closed connection, response will be skipped", 0.01);
        }

        return connectionClosed;
    }
}
