package org.prebid.server.metric.prebid;

import io.vertx.core.AsyncResult;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.auction.model.AdapterResponse;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.AccountMetrics;
import org.prebid.server.metric.AdapterMetrics;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.PreBidResponse;
import org.prebid.server.settings.model.Account;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;

public class AuctionHandlerMetrics extends RequestHandlerMetrics {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    public AuctionHandlerMetrics(Metrics metrics, Clock clock) {
        super(metrics, clock);
    }

    public Tuple2<PreBidRequestContext, Account> updateAccountRequestAndRequestTimeMetric(
            Tuple2<PreBidRequestContext, Account> preBidRequestContextAccount, RoutingContext context) {

        final String accountId = preBidRequestContextAccount.getLeft().getPreBidRequest().getAccountId();
        getMetrics().forAccount(accountId).incCounter(MetricName.requests);

        setupRequestTimeUpdater(context);
        return preBidRequestContextAccount;
    }

    private void setupRequestTimeUpdater(RoutingContext context) {
        // set up handler to update request time metric when response is sent back to a client
        final long requestStarted = getClock().millis();
        context.response().endHandler(ignoredVoid -> getMetrics().updateTimer(MetricName.request_time,
                getClock().millis() - requestStarted));
    }

    public void updateAdapterRequestMetrics(String bidder, String accountId) {
        getMetrics().forAdapter(bidder).incCounter(MetricName.requests);
        getMetrics().forAccount(accountId).forAdapter(bidder).incCounter(MetricName.requests);
    }

    public void updateErrorMetrics(AdapterResponse adapterResponse, PreBidRequestContext preBidRequestContext) {
        final BidderStatus bidderStatus = adapterResponse.getBidderStatus();
        final String bidder = bidderStatus.getBidder();

        final AdapterMetrics adapterMetrics = getMetrics().forAdapter(bidder);
        final AdapterMetrics accountAdapterMetrics = getMetrics()
                .forAccount(preBidRequestContext.getPreBidRequest().getAccountId())
                .forAdapter(bidder);

        if (adapterResponse.isTimedOut()) {
            adapterMetrics.incCounter(MetricName.timeout_requests);
            accountAdapterMetrics.incCounter(MetricName.timeout_requests);
        } else {
            adapterMetrics.incCounter(MetricName.error_requests);
            accountAdapterMetrics.incCounter(MetricName.error_requests);
        }
    }

    public void updateResponseTimeMetrics(BidderStatus bidderStatus, PreBidRequestContext preBidRequestContext) {
        final String bidder = bidderStatus.getBidder();

        final Integer responseTimeMs = bidderStatus.getResponseTimeMs();
        getMetrics().forAdapter(bidder).updateTimer(MetricName.request_time, responseTimeMs);
        getMetrics().forAccount(preBidRequestContext.getPreBidRequest().getAccountId())
                .forAdapter(bidder).updateTimer(MetricName.request_time, responseTimeMs);
    }

    public void updateBidResultMetrics(AdapterResponse adapterResponse, PreBidRequestContext preBidRequestContext) {
        final BidderStatus bidderStatus = adapterResponse.getBidderStatus();
        final String bidder = bidderStatus.getBidder();

        final AdapterMetrics adapterMetrics = getMetrics().forAdapter(bidder);
        final AccountMetrics accountMetrics =
                getMetrics().forAccount(preBidRequestContext.getPreBidRequest().getAccountId());
        final AdapterMetrics accountAdapterMetrics = accountMetrics.forAdapter(bidder);

        for (final Bid bid : adapterResponse.getBids()) {
            final long cpm = bid.getPrice().multiply(THOUSAND).longValue();
            adapterMetrics.updateHistogram(MetricName.prices, cpm);
            accountMetrics.updateHistogram(MetricName.prices, cpm);
            accountAdapterMetrics.updateHistogram(MetricName.prices, cpm);
        }

        final Integer numBids = bidderStatus.getNumBids();
        if (numBids != null) {
            accountMetrics.incCounter(MetricName.bids_received, numBids);
            accountAdapterMetrics.incCounter(MetricName.bids_received, numBids);
        } else if (Objects.equals(bidderStatus.getNoBid(), Boolean.TRUE)) {
            adapterMetrics.incCounter(MetricName.no_bid_requests);
            accountAdapterMetrics.incCounter(MetricName.no_bid_requests);
        }

        if (Objects.equals(bidderStatus.getNoCookie(), Boolean.TRUE)) {
            adapterMetrics.incCounter(MetricName.no_cookie_requests);
            accountAdapterMetrics.incCounter(MetricName.no_cookie_requests);
        }
    }

    public PreBidResponse bidResponseOrError(AsyncResult<PreBidResponse> responseResult) {
        if (responseResult.succeeded()) {
            return responseResult.result();
        } else {
            getMetrics().incCounter(MetricName.error_requests);
            return error(errorStatus(responseResult));
        }
    }

    public static PreBidResponse error(String status) {
        return PreBidResponse.builder().status(status).build();
    }

    public static String errorStatus(AsyncResult<PreBidResponse> responseResult) {
        final Throwable exception = responseResult.cause();
        return (exception != null && exception instanceof PreBidException) ? exception.getMessage()
                : "Unexpected server error";
    }

}
