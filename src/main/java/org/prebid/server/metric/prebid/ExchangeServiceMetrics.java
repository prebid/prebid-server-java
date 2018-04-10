package org.prebid.server.metric.prebid;

import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.metric.AdapterMetrics;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ExchangeServiceMetrics {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final Metrics metrics;
    private final BidderCatalog bidderCatalog;

    public ExchangeServiceMetrics(Metrics metrics, BidderCatalog bidderCatalog) {
        this.metrics = Objects.requireNonNull(metrics, "Metrics can not be null");
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog, "BidderCatalog can not be null");
    }

    /**
     * Updates 'request' and 'no_cookie_requests' metrics for each {@link BidderRequest}
     */
    public void updateRequestMetric(List<BidderRequest> bidderRequests, UidsCookie uidsCookie,
                                     Map<String, String> aliases) {
        for (BidderRequest bidderRequest : bidderRequests) {
            final String bidder = aliases.getOrDefault(bidderRequest.getBidder(), bidderRequest.getBidder());

            metrics.forAdapter(bidder).incCounter(MetricName.requests);

            final boolean noBuyerId = !bidderCatalog.isValidName(bidder) || StringUtils.isBlank(
                    uidsCookie.uidFrom(bidderCatalog.usersyncerByName(bidder).cookieFamilyName()));

            if (bidderRequest.getBidRequest().getApp() == null && noBuyerId) {
                metrics.forAdapter(bidder).incCounter(MetricName.no_cookie_requests);
            }
        }
    }

    /**
     * Updates 'request_time', 'responseTime', 'timeout_request', 'error_requests', 'no_bid_requests',
     * 'prices' metrics for each {@link BidderResponse}
     */
    public void updateMetricsFromResponses(List<BidderResponse> bidderResponses) {
        for (BidderResponse bidderResponse : bidderResponses) {
            final String bidder = bidderResponse.getBidder();
            final AdapterMetrics adapterMetrics = metrics.forAdapter(bidder);

            adapterMetrics.updateTimer(MetricName.request_time, bidderResponse.getResponseTime());

            final List<BidderBid> bidderBids = bidderResponse.getSeatBid().getBids();
            final List<Bid> bids = CollectionUtils.isNotEmpty(bidderBids)
                    ? bidderBids.stream().map(BidderBid::getBid).collect(Collectors.toList())
                    : null;

            if (CollectionUtils.isEmpty(bids)) {
                adapterMetrics.incCounter(MetricName.no_bid_requests);
            } else {
                for (Bid bid : bids) {
                    final long cpmPrice = bid.getPrice() != null
                            ? bid.getPrice().multiply(THOUSAND).longValue()
                            : 0L;
                    adapterMetrics.updateHistogram(MetricName.prices, cpmPrice);
                }
            }

            final List<BidderError> errors = bidderResponse.getSeatBid().getErrors();
            if (CollectionUtils.isNotEmpty(errors)) {
                for (BidderError error : errors) {
                    adapterMetrics.incCounter(error.isTimedOut()
                            ? MetricName.timeout_requests
                            : MetricName.error_requests);
                }
            }
        }
    }
}
