package org.prebid.server.handler.openrtb2.aspects;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.handler.openrtb2.VideoHandler;
import org.prebid.server.spring.config.WebConfiguration;

public class QueuedRequestTimeout implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VideoHandler.class);

    private final VideoHandler videoHandler;
    private final WebConfiguration.RequestTimeoutHeaders requestTimeoutHeaders;

    public QueuedRequestTimeout(VideoHandler videoHandler, WebConfiguration.RequestTimeoutHeaders requestTimeoutHeaders) {
        this.videoHandler = videoHandler;
        this.requestTimeoutHeaders = requestTimeoutHeaders;
    }

    @Override
    public void handle(RoutingContext context) {
        final MultiMap headers = context.request().headers();
        final String reqTimeInQueue = headers.get(requestTimeoutHeaders.getRequestTimeInQueue());
        final String reqTimeout = headers.get(requestTimeoutHeaders.getRequestTimeoutInQueue());

        if (StringUtils.isEmpty(reqTimeInQueue) || StringUtils.isEmpty(reqTimeout)) {
            videoHandler.handle(context);
            return;
        }

        try {
            final float reqTimeFloat = Float.parseFloat(reqTimeInQueue);
            final float reqTimeoutFloat = Float.parseFloat(reqTimeout);

            if (reqTimeFloat >= reqTimeoutFloat) {
                respondWith(context, HttpResponseStatus.REQUEST_TIMEOUT.code(),
                        "Queued request processing time exceeded maximum");
                return;
            }
        } catch (NumberFormatException e) {
            respondWith(context, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(),
                    "Request timeout headers are incorrect (wrong format)");
            return;
        }

        videoHandler.handle(context);
    }

    private void respondWith(RoutingContext context, int status, String body) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
        } else {
            context.response()
                    .setStatusCode(status)
                    .end(body);
        }
    }
}
