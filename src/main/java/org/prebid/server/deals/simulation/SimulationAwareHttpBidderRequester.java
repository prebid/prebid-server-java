package org.prebid.server.deals.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderErrorNotifier;
import org.prebid.server.bidder.BidderRequestCompletionTrackerFactory;
import org.prebid.server.bidder.HttpBidderRequestEnricher;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.vertx.http.HttpClient;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimulationAwareHttpBidderRequester extends HttpBidderRequester {

    private static final BigDecimal DEFAULT_CPM = BigDecimal.ONE;
    private static final String DEFAULT_ADM = "<Impression><![CDATA[]]></Impression>";
    private static final String DEFAULT_CRID = "crid";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String BID_ID_FORMAT = "%s-%s";

    private final Map<String, Double> bidRates;
    private final LineItemService lineItemService;
    private final JacksonMapper mapper;

    public SimulationAwareHttpBidderRequester(
            HttpClient httpClient,
            BidderRequestCompletionTrackerFactory bidderRequestCompletionTrackerFactory,
            BidderErrorNotifier bidderErrorNotifier,
            HttpBidderRequestEnricher requestEnricher,
            LineItemService lineItemService,
            JacksonMapper mapper) {

        super(httpClient, bidderRequestCompletionTrackerFactory, bidderErrorNotifier, requestEnricher, mapper);

        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.mapper = Objects.requireNonNull(mapper);
        this.bidRates = new HashMap<>();
    }

    void setBidRates(Map<String, Double> bidRates) {
        this.bidRates.putAll(bidRates);
    }

    @Override
    public <T> Future<BidderSeatBid> requestBids(Bidder<T> bidder,
                                                 BidderRequest bidderRequest,
                                                 Timeout timeout,
                                                 CaseInsensitiveMultiMap requestHeaders,
                                                 boolean debugEnabled) {

        final List<Imp> imps = bidderRequest.getBidRequest().getImp();
        final Map<String, Imp> idToImps = imps.stream().collect(Collectors.toMap(Imp::getId, Function.identity()));
        final Map<String, Set<DealInfo>> impsToDealInfo = imps.stream()
                .filter(imp -> imp.getPmp() != null)
                .collect(Collectors.toMap(Imp::getId, imp -> imp.getPmp().getDeals().stream()
                        .map(deal -> DealInfo.of(deal.getId(), getLineItemId(deal)))
                        .filter(dealInfo -> dealInfo.getLineItemId() != null)
                        .collect(Collectors.toSet())));

        if (impsToDealInfo.values().stream().noneMatch(CollectionUtils::isNotEmpty)) {
            return Future.succeededFuture(BidderSeatBid.of(Collections.emptyList(), Collections.emptyList(),
                    Collections.singletonList(BidderError.failedToRequestBids(
                            "Matched or ready to serve line items were not found, but required in simulation mode"))));
        }

        final List<BidderBid> bidderBids = impsToDealInfo.entrySet().stream()
                .flatMap(impToDealInfo -> impToDealInfo.getValue()
                        .stream()
                        .map(dealInfo -> createBid(idToImps.get(impToDealInfo.getKey()), dealInfo.getDealId(),
                                dealInfo.getLineItemId()))
                        .filter(Objects::nonNull))
                .map(bid -> BidderBid.of(bid, BidType.banner, DEFAULT_CURRENCY))
                .collect(Collectors.toList());

        return Future.succeededFuture(BidderSeatBid.of(bidderBids, Collections.emptyList(), Collections.emptyList()));
    }

    private String getLineItemId(Deal deal) {
        final JsonNode extDealNode = deal.getExt();
        final ExtDeal extDeal = extDealNode != null ? getExtDeal(extDealNode) : null;
        final ExtDealLine extDealLine = extDeal != null ? extDeal.getLine() : null;
        return extDealLine != null ? extDealLine.getLineItemId() : null;
    }

    private Bid createBid(Imp imp, String dealId, String lineItemId) {
        final Double rate = bidRates.get(lineItemId);
        if (rate == null) {
            throw new PreBidException(String.format("Bid rate for line item with id %s was not found", lineItemId));
        }
        final String impId = imp.getId();
        final LineItem lineItem = lineItemService.getLineItemById(lineItemId);
        final List<Format> sizes = getLineItemSizes(imp);
        return Math.random() < rate
                ? Bid.builder()
                .id(String.format(BID_ID_FORMAT, impId, lineItemId))
                .impid(impId)
                .dealid(dealId)
                .price(lineItem != null ? lineItem.getCpm() : DEFAULT_CPM)
                .adm(DEFAULT_ADM)
                .crid(DEFAULT_CRID)
                .w(sizes.isEmpty() ? 0 : sizes.get(0).getW())
                .h(sizes.isEmpty() ? 0 : sizes.get(0).getH())
                .build()
                : null;
    }

    private List<Format> getLineItemSizes(Imp imp) {
        return imp.getPmp().getDeals().stream()
                .map(Deal::getExt)
                .filter(Objects::nonNull)
                .map(this::getExtDeal)
                .filter(Objects::nonNull)
                .map(ExtDeal::getLine)
                .filter(Objects::nonNull)
                .map(ExtDealLine::getSizes)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ExtDeal getExtDeal(JsonNode extDeal) {
        try {
            return mapper.mapper().treeToValue(extDeal, ExtDeal.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(
                    String.format("Error decoding bidRequest.imp.pmp.deal.ext: %s", e.getMessage()), e);
        }
    }

    @Value
    @AllArgsConstructor(staticName = "of")
    private static class DealInfo {

        String dealId;

        String lineItemId;
    }
}
