package org.rtb.vexing.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.HttpConnector;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.BidderSeatBid;
import org.rtb.vexing.model.openrtb.ext.ExtPrebid;
import org.rtb.vexing.model.openrtb.ext.response.ExtBidPrebid;
import org.rtb.vexing.model.openrtb.ext.response.ExtBidResponse;
import org.rtb.vexing.model.openrtb.ext.response.ExtHttpCall;

import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.rtb.vexing.model.openrtb.ext.response.BidType.banner;

public class ExchangeServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpConnector httpConnector;
    @Mock
    private BidderCatalog bidderCatalog;

    private ExchangeService exchangeService;

    @Before
    public void setUp() {
        exchangeService = new ExchangeService(httpConnector, bidderCatalog);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new ExchangeService(null, null));
        assertThatNullPointerException().isThrownBy(() -> new ExchangeService(httpConnector, null));
    }

    @Test
    public void shouldTolerateImpWithoutExtension() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().build())).build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        verifyZeroInteractions(bidderCatalog);
        verifyZeroInteractions(httpConnector);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateImpWithUnknownBidderInExtension() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(singletonMap("invalid", 0)))
                        .build()))
                .build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        verify(bidderCatalog).isValidName(eq("invalid"));
        verifyNoMoreInteractions(bidderCatalog);
        verifyZeroInteractions(httpConnector);
        assertThat(bidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateMissingPrebidImpExtension() {
        // given
        givenHttpConnector(emptyResponse());

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(singletonMap("someBidder", 1)))
                        .build()))
                .build();

        // when
        exchangeService.holdAuction(bidRequest);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpConnector).requestBids(any(), bidRequestCaptor.capture());
        final BidRequest capturedBidRequest = bidRequestCaptor.getValue();
        assertThat(capturedBidRequest.getImp()).hasSize(1)
                .element(0).returns(mapper.valueToTree(ExtPrebid.of(null, 1)), Imp::getExt);
    }

    @Test
    public void shouldExtractRequestWithBidderSpecificExtension() {
        // given
        givenHttpConnector(emptyResponse());

        final BidRequest bidRequest = BidRequest.builder()
                .id("requestId")
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build())
                        .ext(mapper.valueToTree(doubleMap("prebid", 0, "someBidder", 1)))
                        .build()))
                .tmax(500L)
                .build();

        // when
        exchangeService.holdAuction(bidRequest);

        // then
        final ArgumentCaptor<BidRequest> bidRequestCaptor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpConnector).requestBids(any(), bidRequestCaptor.capture());
        final BidRequest capturedBidRequest = bidRequestCaptor.getValue();
        assertThat(capturedBidRequest).isEqualTo(BidRequest.builder()
                .id("requestId")
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(0, 1)))
                        .build()))
                .tmax(500L)
                .build());
    }

    @Test
    public void shouldExtractMultipleRequests() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        final Bidder bidder1 = mock(Bidder.class);
        given(bidderCatalog.byName(eq("bidder1"))).willReturn(bidder1);
        final Bidder bidder2 = mock(Bidder.class);
        given(bidderCatalog.byName(eq("bidder2"))).willReturn(bidder2);
        given(httpConnector.requestBids(any(), any())).willReturn(Future.succeededFuture(emptyResponse()));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder()
                                .ext(mapper.valueToTree(doubleMap("bidder1", 1, "bidder2", 2)))
                                .build(),
                        Imp.builder()
                                .ext(mapper.valueToTree(singletonMap("bidder1", 3)))
                                .build()))
                .build();

        // when
        exchangeService.holdAuction(bidRequest);

        // then
        final ArgumentCaptor<BidRequest> bidRequest1Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpConnector).requestBids(same(bidder1), bidRequest1Captor.capture());
        final BidRequest capturedBidRequest1 = bidRequest1Captor.getValue();
        assertThat(capturedBidRequest1.getImp()).hasSize(2)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .containsOnly(1, 3);

        final ArgumentCaptor<BidRequest> bidRequest2Captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(httpConnector).requestBids(same(bidder2), bidRequest2Captor.capture());
        final BidRequest capturedBidRequest2 = bidRequest2Captor.getValue();
        assertThat(capturedBidRequest2.getImp()).hasSize(1)
                .element(0).returns(2, imp -> imp.getExt().get("bidder").asInt());
    }

    @Test
    public void shouldSpecifyNbrInResponseIfNoValidBidders() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(emptyList()).build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        assertThat(bidResponse).returns(2, BidResponse::getNbr);
    }

    @Test
    public void shouldTolerateBidderResultWithoutBids() {
        // given
        givenHttpConnector(emptyResponse());

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(singletonMap("someBidder", 1)))
                        .build()))
                .build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        assertThat(bidResponse.getSeatbid()).isEmpty();
    }

    @Test
    public void shouldReturnPopulatedSeatBid() {
        // given
        givenHttpConnector(BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId").ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner)),
                mapper.valueToTree(singletonMap("seatBidExt", 2)),
                emptyList(),
                emptyList()));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(singletonMap("someBidder", 1)))
                        .build()))
                .build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.builder().type(banner).build(), singletonMap("bidExt", 1))))
                        .build()))
                .ext(mapper.valueToTree(ExtPrebid.of(null, singletonMap("seatBidExt", 2))))
                .build());
    }

    @Test
    public void shouldTolerateMissingExtInSeatBidAndBid() {
        // given
        givenHttpConnector(BidderSeatBid.of(singletonList(BidderBid.of(Bid.builder().id("bidId").build(), banner)),
                null, emptyList(), emptyList()));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(singletonMap("someBidder", 1)))
                        .build()))
                .build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1).element(0).isEqualTo(SeatBid.builder()
                .seat("someBidder")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder().type(banner).build(), null)))
                        .build()))
                .build());
    }

    @Test
    public void shouldReturnMultipleSeatBids() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(httpConnector.requestBids(any(), any()))
                .willReturn(Future.succeededFuture(BidderSeatBid.of(
                        asList(BidderBid.of(Bid.builder().build(), null), BidderBid.of(Bid.builder().build(), null)),
                        null, emptyList(), emptyList())))
                .willReturn(Future.succeededFuture(BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().build(), null)),
                        null, emptyList(), emptyList())));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(doubleMap("bidder1", 1, "bidder2", 2)))
                        .build()))
                .build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(2)
                .extracting(seatBid -> seatBid.getBid().size()).containsOnly(2, 1);
    }

    @Test
    public void shouldPopulateBidResponseExtension() throws JsonProcessingException {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        final Bidder bidder1 = mock(Bidder.class);
        given(bidderCatalog.byName(eq("bidder1"))).willReturn(bidder1);
        final Bidder bidder2 = mock(Bidder.class);
        given(bidderCatalog.byName(eq("bidder2"))).willReturn(bidder2);
        given(httpConnector.requestBids(same(bidder1), any()))
                .willReturn(Future.succeededFuture(BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().build(), null)), null, emptyList(),
                        singletonList("bidder1_error1"))));
        given(httpConnector.requestBids(same(bidder2), any()))
                .willReturn(Future.succeededFuture(BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().build(), null)), null, emptyList(),
                        asList("bidder2_error1", "bidder2_error2"))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(doubleMap("bidder1", 1, "bidder2", 2)))
                        .build()))
                .build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.responsetimemillis).hasSize(2).containsOnlyKeys("bidder1", "bidder2");
        assertThat(ext.errors).hasSize(2).containsOnly(
                entry("bidder1", singletonList("bidder1_error1")),
                entry("bidder2", asList("bidder2_error1", "bidder2_error2")));
    }

    @Test
    public void shouldPopulateBidResponseDebugExtensionIfTestFlagIsTrue() throws JsonProcessingException {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        final Bidder bidder1 = mock(Bidder.class);
        given(bidderCatalog.byName(eq("bidder1"))).willReturn(bidder1);
        final Bidder bidder2 = mock(Bidder.class);
        given(bidderCatalog.byName(eq("bidder2"))).willReturn(bidder2);
        given(httpConnector.requestBids(same(bidder1), any()))
                .willReturn(Future.succeededFuture(BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().build(), null)), null,
                        singletonList(ExtHttpCall.builder()
                                .uri("bidder1_uri1")
                                .requestbody("bidder1_requestBody1")
                                .status(200)
                                .responsebody("bidder1_responseBody1")
                                .build()),
                        emptyList())));
        given(httpConnector.requestBids(same(bidder2), any()))
                .willReturn(Future.succeededFuture(BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().build(), null)), null,
                        asList(
                                ExtHttpCall.builder()
                                        .uri("bidder2_uri1")
                                        .requestbody("bidder2_requestBody1")
                                        .status(200)
                                        .responsebody("bidder2_responseBody1")
                                        .build(),
                                ExtHttpCall.builder()
                                        .uri("bidder2_uri2")
                                        .requestbody("bidder2_requestBody2")
                                        .status(404)
                                        .responsebody("bidder2_responseBody2")
                                        .build()),
                        emptyList())));

        final BidRequest bidRequest = BidRequest.builder()
                .test(1)
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(doubleMap("bidder1", 1, "bidder2", 2)))
                        .build()))
                .build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.debug).isNotNull();
        assertThat(ext.debug.httpcalls).hasSize(2).containsOnly(
                entry("bidder1", singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build())),
                entry("bidder2", asList(
                        ExtHttpCall.builder()
                                .uri("bidder2_uri1")
                                .requestbody("bidder2_requestBody1")
                                .status(200)
                                .responsebody("bidder2_responseBody1")
                                .build(),
                        ExtHttpCall.builder()
                                .uri("bidder2_uri2")
                                .requestbody("bidder2_requestBody2")
                                .status(404)
                                .responsebody("bidder2_responseBody2")
                                .build())));
    }

    @Test
    public void shouldNotPopulateBidResponseDebugExtensionIfTestFlagIsFalse() throws JsonProcessingException {
        // given
        givenHttpConnector(BidderSeatBid.of(
                singletonList(BidderBid.of(Bid.builder().build(), null)), null,
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList()));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.valueToTree(singletonMap("bidder1", 1))).build()))
                .build();

        // when
        final BidResponse bidResponse = exchangeService.holdAuction(bidRequest).result();

        // then
        final ExtBidResponse ext = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);
        assertThat(ext.debug).isNull();
    }

    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private void givenHttpConnector(BidderSeatBid response) {
        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(httpConnector.requestBids(any(), any())).willReturn(Future.succeededFuture(response));
    }

    private static BidderSeatBid emptyResponse() {
        return BidderSeatBid.of(emptyList(), null, emptyList(), emptyList());
    }
}
