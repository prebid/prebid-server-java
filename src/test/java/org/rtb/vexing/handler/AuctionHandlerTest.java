package org.rtb.vexing.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.request.AdUnit;
import org.rtb.vexing.model.request.Bid;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.PreBidResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class AuctionHandlerTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private ObjectMapper mapper = new ObjectMapper();

    @Mock
    private AdapterCatalog adapterCatalog;
    @Mock
    private Adapter rubiconAdapter;
    @Mock
    private Adapter appnexusAdapter;
    @Mock
    private Vertx vertx;
    private AuctionHandler auctionHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(adapterCatalog.get(eq(RUBICON))).willReturn(rubiconAdapter);
        given(adapterCatalog.get(eq(APPNEXUS))).willReturn(appnexusAdapter);

        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(AdditionalAnswers.<Long, Handler<Long>>answerVoid((p, h) -> h.handle(0L)));

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);

        auctionHandler = new AuctionHandler(adapterCatalog, vertx);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(null, null));
        assertThatNullPointerException().isThrownBy(() -> new AuctionHandler(adapterCatalog, null));
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(null);

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(httpResponse, times(1)).setStatusCode(eq(400));
        verify(httpResponse, times(1)).end();
        verifyNoMoreInteractions(httpResponse, adapterCatalog);
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        given(routingContext.getBodyAsJson())
                .willReturn(JsonObject.mapFrom(PreBidRequest.builder().adUnits(emptyList()).build()));

        // when
        auctionHandler.auction(routingContext);

        // then
        verify(httpResponse, times(1))
                .putHeader(eq(new AsciiString("Date")), ArgumentMatchers.<CharSequence>isNotNull());
        verify(httpResponse, times(1))
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithNoBidIfAtLeastOneAdapterFailed() throws IOException {
        // given
        givenPreBidRequestWith2AdUnitsAnd2BidsEach();

        given(rubiconAdapter.requestBids(any(), any(), any())).willReturn(Future.failedFuture("failure"));
        givenAdapterRespondingWithBids(appnexusAdapter, null);

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse).isEqualTo(PreBidResponse.builder().build());
    }

    @Test
    public void shouldRespondWithMultipleBidderStatusesAndBidsWhenMultipleAdUnitsAndBidsInPreBidRequest()
            throws IOException {
        // given
        givenPreBidRequestWith2AdUnitsAnd2BidsEach();

        givenAdapterRespondingWithBids(rubiconAdapter, RUBICON, "bidId1", "bidId2");
        givenAdapterRespondingWithBids(appnexusAdapter, APPNEXUS, "bidId3", "bidId4");

        // when
        auctionHandler.auction(routingContext);

        // then
        final PreBidResponse preBidResponse = capturePreBidResponse();
        assertThat(preBidResponse.status).isEqualTo("OK");
        assertThat(preBidResponse.tid).isEqualTo("tid");
        assertThat(preBidResponse.bidderStatus).extracting(b -> b.bidder).containsOnly(RUBICON, APPNEXUS);
        assertThat(preBidResponse.bids).extracting(b -> b.bidId).containsOnly("bidId1", "bidId2", "bidId3", "bidId4");
    }

    private void givenPreBidRequestWith2AdUnitsAnd2BidsEach() {
        final Bid rubiconBid = Bid.builder().bidder(RUBICON).build();
        final Bid appnexusBid = Bid.builder().bidder(APPNEXUS).build();
        final JsonObject preBidRequest = JsonObject.mapFrom(PreBidRequest.builder()
                .tid("tid")
                .adUnits(asList(
                        AdUnit.builder().bids(asList(rubiconBid, appnexusBid)).build(),
                        AdUnit.builder().bids(asList(rubiconBid, appnexusBid)).build()))
                .build());
        given(routingContext.getBodyAsJson()).willReturn(preBidRequest);
    }

    private void givenAdapterRespondingWithBids(Adapter adapter, String bidder, String... bidIds) {
        given(adapter.requestBids(any(), any(), any()))
                .willReturn(Future.succeededFuture(BidderResult.builder()
                        .bidderStatus(BidderStatus.builder().bidder(bidder).build())
                        .bids(Arrays.stream(bidIds)
                                .map(id -> org.rtb.vexing.model.response.Bid.builder().bidId(id).build())
                                .collect(Collectors.toList()))
                        .build()));
    }

    private PreBidResponse capturePreBidResponse() throws IOException {
        final ArgumentCaptor<String> preBidResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse, times(1)).end(preBidResponseCaptor.capture());
        return mapper.readValue(preBidResponseCaptor.getValue(), PreBidResponse.class);
    }
}
