package org.prebid.server.deals.simulation;

import com.fasterxml.jackson.databind.node.IntNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.BidderErrorNotifier;
import org.prebid.server.bidder.BidderRequestCompletionTrackerFactory;
import org.prebid.server.bidder.HttpBidderRequestEnricher;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.Price;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class SimulationAwareHttpBidderRequesterTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private SimulationAwareHttpBidderRequester bidderRequester;

    @Mock
    private HttpClient httpClient;
    @Mock
    private BidderRequestCompletionTrackerFactory bidderRequestCompletionTrackerFactory;
    @Mock
    private BidderErrorNotifier bidderErrorNotifier;
    @Mock
    private HttpBidderRequestEnricher requestEnricher;
    @Mock
    private CaseInsensitiveMultiMap requestHeaders;
    @Mock
    private LineItemService lineItemService;

    @Before
    public void setUp() {
        bidderRequester = new SimulationAwareHttpBidderRequester(
                httpClient, bidderRequestCompletionTrackerFactory, bidderErrorNotifier, requestEnricher,
                lineItemService, jacksonMapper);
    }

    @Test
    public void requestBidsShouldReturnBidderSeatBidWithOneBidAndFilterOutOne() {
        // given
        final Map<String, Double> rates = new HashMap<>();
        rates.put("lineItemId1", 1.00);
        rates.put("lineItemId2", 0.00);
        bidderRequester.setBidRates(rates);
        given(lineItemService.getLineItemById(eq("lineItemId1"))).willReturn(LineItem.of(
                LineItemMetaData.builder().price(Price.of(BigDecimal.ONE, "USD")).build(), Price.of(BigDecimal.ONE,
                        "USD"), null, null));
        given(lineItemService.getLineItemById(eq("lineItemId2"))).willReturn(LineItem.of(
                LineItemMetaData.builder().price(Price.of(BigDecimal.TEN, "USD")).build(), Price.of(BigDecimal.ONE,
                        "USD"), null, null));

        final BidRequest bidRequest = BidRequest.builder().imp(asList(
                Imp.builder().id("impId1").pmp(Pmp.builder().deals(singletonList(Deal.builder()
                        .id("dealId1")
                        .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of("lineItemId1", null,
                                singletonList(Format.builder().w(100).h(100).build()), null))))
                        .build())).build()).build(),
                Imp.builder().id("impId2").pmp(Pmp.builder().deals(singletonList(Deal.builder()
                        .id("dealId2")
                        .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of("lineItemId2", null,
                                singletonList(Format.builder().w(100).h(100).build()), null))))
                        .build())).build()).build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, bidRequest);

        // when
        final Future<BidderSeatBid> result = bidderRequester.requestBids(null, bidderRequest, null, requestHeaders,
                false);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(singletonList(BidderBid.of(
                Bid.builder().id("impId1-lineItemId1").impid("impId1").dealid("dealId1").price(BigDecimal.ONE)
                        .adm("<Impression><![CDATA[]]></Impression>").crid("crid").w(100).h(100)
                        .build(), BidType.banner, "USD")), Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void requestBidsShouldFilterBidderSeatBidForWhichImpPmpIsNull() {
        // given
        bidderRequester.setBidRates(Collections.singletonMap("lineItemId1", 1.00));
        given(lineItemService.getLineItemById(eq("lineItemId1"))).willReturn(LineItem.of(
                LineItemMetaData.builder().price(Price.of(BigDecimal.ONE, "USD")).build(),
                Price.of(BigDecimal.ONE, "USD"), null, null));

        final BidRequest bidRequest = BidRequest.builder().imp(asList(
                Imp.builder().id("impId1").pmp(Pmp.builder().deals(singletonList(Deal.builder()
                        .id("dealId1")
                        .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of("lineItemId1", null,
                                singletonList(Format.builder().w(100).h(100).build()), null))))
                        .build())).build()).build(),
                Imp.builder().id("impId2").build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, bidRequest);

        // when
        final Future<BidderSeatBid> result = bidderRequester.requestBids(null, bidderRequest, null, requestHeaders,
                false);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(singletonList(BidderBid.of(
                Bid.builder().id("impId1-lineItemId1").impid("impId1").dealid("dealId1").price(BigDecimal.ONE)
                        .adm("<Impression><![CDATA[]]></Impression>").crid("crid").w(100).h(100)
                        .build(), BidType.banner, "USD")), Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void requestBidsShouldSetSizesAsZeroIfExtDealsLinesDoesNotHaveSizes() {
        // given
        final Map<String, Double> rates = new HashMap<>();
        rates.put("lineItemId1", 1.00);
        bidderRequester.setBidRates(rates);
        given(lineItemService.getLineItemById(eq("lineItemId1"))).willReturn(LineItem.of(
                LineItemMetaData.builder().price(Price.of(BigDecimal.ONE, "USD")).build(),
                Price.of(BigDecimal.ONE, "USD"), null, null));

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                Imp.builder().id("impId1").pmp(Pmp.builder().deals(singletonList(Deal.builder()
                        .id("dealId1")
                        .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of("lineItemId1", null, null, null))))
                        .build())).build()).build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, bidRequest);

        // when
        final Future<BidderSeatBid> result = bidderRequester.requestBids(null, bidderRequest, null, requestHeaders,
                false);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(singletonList(BidderBid.of(
                Bid.builder().id("impId1-lineItemId1").impid("impId1").dealid("dealId1").price(BigDecimal.ONE)
                        .adm("<Impression><![CDATA[]]></Impression>").crid("crid").w(0).h(0)
                        .build(), BidType.banner, "USD")), Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void requestBidsShouldThrowPrebidExceptionWhenExtDealsInvalidFormat() {
        // given
        final Map<String, Double> rates = new HashMap<>();
        rates.put("lineItemId1", 1.00);
        bidderRequester.setBidRates(rates);
        given(lineItemService.getLineItemById(eq("lineItemId1"))).willReturn(LineItem.of(
                LineItemMetaData.builder().price(Price.of(BigDecimal.ONE, "USD")).build(),
                Price.of(BigDecimal.ONE, "USD"), null, null));

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                Imp.builder().id("impId1").pmp(Pmp.builder().deals(singletonList(Deal.builder()
                        .id("dealId1")
                        .ext(mapper.createObjectNode().set("line", new IntNode(5)))
                        .build())).build()).build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, bidRequest);

        // when and then
        assertThatThrownBy(() -> bidderRequester.requestBids(null, bidderRequest, null, requestHeaders, false))
                .isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Error decoding bidRequest.imp.pmp.deal.ext:");
    }

    @Test
    public void requestBidsShouldReturnBidderSeatBidWithoutBidderBidsAndWithError() {
        // given
        bidderRequester.setBidRates(Collections.singletonMap("lineItemId1", 1.00));

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                Imp.builder().id("impId1").pmp(Pmp.builder().deals(singletonList(Deal.builder()
                        .id("dealId1").build())).build()).build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, bidRequest);

        // when
        final Future<BidderSeatBid> result = bidderRequester.requestBids(null, bidderRequest, null, requestHeaders,
                false);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(Collections.emptyList(), Collections.emptyList(),
                singletonList(BidderError.failedToRequestBids(
                        "Matched or ready to serve line items were not found, but required in simulation mode"))));
    }

    @Test
    public void requestBidsShouldThrowPrebidExceptionWhenBidRatesForLineItemWereNotFound() {
        // given
        bidderRequester.setBidRates(Collections.singletonMap("lineItemId1", 1.00));

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                Imp.builder().id("impId1").pmp(Pmp.builder().deals(singletonList(Deal.builder()
                        .id("dealId1")
                        .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of("lineItemId2", null, null, null))))
                        .build())).build()).build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("bidder", null, bidRequest);

        // when
        assertThatThrownBy(() -> bidderRequester.requestBids(null, bidderRequest, null, requestHeaders, false))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Bid rate for line item with id lineItemId2 was not found");
    }

    @Test
    public void setBidRatesShouldMergeRates() {
        // given
        final Map<String, Double> initialBidRates = new HashMap<>();
        initialBidRates.put("lineItemId1", 1.00);
        initialBidRates.put("lineItemId2", 0.5);
        bidderRequester.setBidRates(initialBidRates);

        final Map<String, Double> updateBidRates = new HashMap<>();
        updateBidRates.put("lineItemId1", 0.00);
        updateBidRates.put("lineItemId3", 0.75);

        // when
        bidderRequester.setBidRates(updateBidRates);

        // then
        @SuppressWarnings("unchecked") final Map<String, Double> resultBidRates =
                (Map<String, Double>) ReflectionTestUtils.getField(bidderRequester, "bidRates");

        assertThat(resultBidRates).isNotNull();
        assertThat(resultBidRates.entrySet()).hasSize(3)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("lineItemId1", 0.00), tuple("lineItemId2", 0.5), tuple("lineItemId3", 0.75));
    }
}
