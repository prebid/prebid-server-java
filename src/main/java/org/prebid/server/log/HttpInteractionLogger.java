package org.prebid.server.log;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.log.model.HttpLogSpec;
import org.prebid.server.settings.model.Account;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class HttpInteractionLogger {

    private static final Logger logger = LoggerFactory.getLogger(HttpInteractionLogger.class);

    private final AtomicReference<HttpLogSpec> httpLogSpec = new AtomicReference<>();
    private final AtomicLong loggedInteractions = new AtomicLong(0);

    public void setSpec(HttpLogSpec spec) {
        httpLogSpec.set(spec);
        loggedInteractions.set(0);
    }

    public void maybeLogOpenrtb2Auction(AuctionContext auctionContext,
                                        RoutingContext routingContext,
                                        int statusCode,
                                        String responseBody) {

        if (interactionSatisfySpec(HttpLogSpec.Endpoint.auction, statusCode, auctionContext)) {
            logger.info(
                    "Requested URL: \"{0}\", request body: \"{1}\", response status: \"{2}\", response body: \"{3}\"",
                    routingContext.request().uri(),
                    routingContext.getBody().toString(),
                    statusCode,
                    responseBody);

            incLoggedInteractions();
        }
    }

    public void maybeLogOpenrtb2Amp(AuctionContext auctionContext,
                                    RoutingContext routingContext,
                                    int statusCode,
                                    String responseBody) {

        if (interactionSatisfySpec(HttpLogSpec.Endpoint.amp, statusCode, auctionContext)) {
            logger.info(
                    "Requested URL: \"{0}\", response status: \"{1}\", response body: \"{2}\"",
                    routingContext.request().uri(),
                    statusCode,
                    responseBody);

            incLoggedInteractions();
        }
    }

    public boolean interactionSatisfySpec(HttpLogSpec.Endpoint requestedEndpoint,
                                          int statusCode,
                                          AuctionContext auctionContext) {

        final HttpLogSpec spec = httpLogSpec.get();
        if (spec == null) {
            return false;
        }

        final Account account = auctionContext != null ? auctionContext.getAccount() : null;
        final String accountId = account != null ? account.getId() : null;

        return (spec.getEndpoint() == null || spec.getEndpoint() == requestedEndpoint)
                && (spec.getStatusCode() == null || spec.getStatusCode() == statusCode)
                && (spec.getAccount() == null || spec.getAccount().equals(accountId));
    }

    private void incLoggedInteractions() {
        final HttpLogSpec spec = httpLogSpec.get();
        if (spec != null && loggedInteractions.incrementAndGet() >= spec.getLimit()) {
            httpLogSpec.set(null);
            loggedInteractions.set(0);
        }
    }
}
