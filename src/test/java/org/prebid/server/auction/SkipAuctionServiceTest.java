package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class SkipAuctionServiceTest {

    @Mock
    private StoredResponseProcessor storedResponseProcessor;
    @Mock
    private BidResponseCreator bidResponseCreator;
    @Mock
    private Timeout timeout;

    private SkipAuctionService target;

    @BeforeEach
    public void setUp() {
        target = new SkipAuctionService(storedResponseProcessor, bidResponseCreator);
    }

    @Test
    public void skipAuctionShouldReturnFailedFutureWhenRequestIsRejected() {
        // given
        final AuctionContext givenAuctionContext = AuctionContext.builder()
                .requestRejected(true)
                .build();

        // when
        final Future<AuctionContext> result = target.skipAuction(givenAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();
        final BidResponse expectedBidResponse = BidResponse.builder().seatbid(emptyList()).build();
        assertThat(result.result()).isEqualTo(givenAuctionContext.with(expectedBidResponse));

        verifyNoInteractions(storedResponseProcessor, bidResponseCreator);
    }

    @Test
    public void skipAuctionShouldReturnFailedFutureWhenBidRequestExtIsNull() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().ext(null).build())
                .build();

        // when
        final Future<AuctionContext> result = target.skipAuction(auctionContext);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("the auction can not be skipped, ext.prebid.storedauctionresponse is absent");

        verifyNoInteractions(storedResponseProcessor, bidResponseCreator);
    }

    @Test
    public void skipAuctionShouldReturnFailedFutureWhenBidRequestExtPrebidIsNull() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().ext(ExtRequest.empty()).build())
                .build();

        // when
        final Future<AuctionContext> result = target.skipAuction(auctionContext);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("the auction can not be skipped, ext.prebid.storedauctionresponse is absent");

        verifyNoInteractions(storedResponseProcessor, bidResponseCreator);
    }

    @Test
    public void skipAuctionShouldReturnFailedFutureWhenStoredResponseIsNull() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().storedAuctionResponse(null).build()))
                        .build())
                .build();

        // when
        final Future<AuctionContext> result = target.skipAuction(auctionContext);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("the auction can not be skipped, ext.prebid.storedauctionresponse is absent");

        verifyNoInteractions(storedResponseProcessor, bidResponseCreator);
    }

    @Test
    public void skipAuctionShouldReturnFailedFutureWhenStoredResponseSeatBidAndIdAreNull() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .storedAuctionResponse(ExtStoredAuctionResponse.of(null, null))
                                .build()))
                        .build())
                .build();

        // when
        final Future<AuctionContext> result = target.skipAuction(auctionContext);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("the auction can not be skipped, "
                        + "ext.prebid.storedauctionresponse can not be resolved properly");

        verifyNoInteractions(storedResponseProcessor, bidResponseCreator);
    }

    @Test
    public void skipAuctionShouldReturnBidResponseWithSeatBidsFromStoredAuctionResponse() {
        // given
        final List<SeatBid> givenSeatBids = givenSeatBids("bidId1", "bidId2");
        final ExtStoredAuctionResponse givenStoredResponse = ExtStoredAuctionResponse.of("id", givenSeatBids);
        final AuctionContext givenAuctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .storedAuctionResponse(givenStoredResponse)
                                .build()))
                        .build())
                .build();

        final BidResponse givenBidResponse = BidResponse.builder().build();
        given(bidResponseCreator.createOnSkippedAuction(any(), any()))
                .willReturn(Future.succeededFuture(givenBidResponse));

        // when
        final Future<AuctionContext> result = target.skipAuction(givenAuctionContext);

        // then
        final AuctionContext expectedAuctionContext = givenAuctionContext.toBuilder()
                .debugWarnings(singletonList("no auction. response defined by storedauctionresponse"))
                .build();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(expectedAuctionContext.with(givenBidResponse).skipAuction());

        verify(bidResponseCreator).createOnSkippedAuction(expectedAuctionContext, givenSeatBids);
        verifyNoInteractions(storedResponseProcessor);
    }

    @Test
    public void skipAuctionShouldReturnEmptySeatBidsWhenSeatBidIsNull() {
        // given
        final ExtStoredAuctionResponse givenStoredResponse = ExtStoredAuctionResponse.of("id", singletonList(null));
        final AuctionContext givenAuctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .storedAuctionResponse(givenStoredResponse)
                                .build()))
                        .build())
                .build();

        final BidResponse givenBidResponse = BidResponse.builder().build();
        given(bidResponseCreator.createOnSkippedAuction(any(), any()))
                .willReturn(Future.succeededFuture(givenBidResponse));

        // when
        final Future<AuctionContext> result = target.skipAuction(givenAuctionContext);

        // then
        final AuctionContext expectedAuctionContext = givenAuctionContext.toBuilder()
                .debugWarnings(List.of(
                        "SeatBid can't be null in stored response",
                        "no auction. response defined by storedauctionresponse"))
                .build();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(expectedAuctionContext.with(givenBidResponse).skipAuction());

        verify(bidResponseCreator).createOnSkippedAuction(expectedAuctionContext, emptyList());
        verifyNoInteractions(storedResponseProcessor);
    }

    @Test
    public void skipAuctionShouldReturnEmptySeatBidsWhenSeatIsEmpty() {
        // given
        final List<SeatBid> givenSeatBids = singletonList(SeatBid.builder().seat("").build());
        final ExtStoredAuctionResponse givenStoredResponse = ExtStoredAuctionResponse.of("id", givenSeatBids);
        final AuctionContext givenAuctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .storedAuctionResponse(givenStoredResponse)
                                .build()))
                        .build())
                .build();

        final BidResponse givenBidResponse = BidResponse.builder().build();
        given(bidResponseCreator.createOnSkippedAuction(any(), any()))
                .willReturn(Future.succeededFuture(givenBidResponse));

        // when
        final Future<AuctionContext> result = target.skipAuction(givenAuctionContext);

        // then
        final AuctionContext expectedAuctionContext = givenAuctionContext.toBuilder()
                .debugWarnings(List.of(
                        "Seat can't be empty in stored response seatBid",
                        "no auction. response defined by storedauctionresponse"))
                .build();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(expectedAuctionContext.with(givenBidResponse).skipAuction());

        verify(bidResponseCreator).createOnSkippedAuction(expectedAuctionContext, emptyList());
        verifyNoInteractions(storedResponseProcessor);
    }

    @Test
    public void skipAuctionShouldReturnEmptySeatBidsWhenBidsAreEmpty() {
        // given
        final List<SeatBid> givenSeatBids = singletonList(SeatBid.builder().seat("seat").bid(emptyList()).build());
        final ExtStoredAuctionResponse givenStoredResponse = ExtStoredAuctionResponse.of("id", givenSeatBids);
        final AuctionContext givenAuctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .storedAuctionResponse(givenStoredResponse)
                                .build()))
                        .build())
                .build();

        final BidResponse givenBidResponse = BidResponse.builder().build();
        given(bidResponseCreator.createOnSkippedAuction(any(), any()))
                .willReturn(Future.succeededFuture(givenBidResponse));

        // when
        final Future<AuctionContext> result = target.skipAuction(givenAuctionContext);

        // then
        final AuctionContext expectedAuctionContext = givenAuctionContext.toBuilder()
                .debugWarnings(List.of(
                        "There must be at least one bid in stored response seatBid",
                        "no auction. response defined by storedauctionresponse"))
                .build();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(expectedAuctionContext.with(givenBidResponse).skipAuction());

        verify(bidResponseCreator).createOnSkippedAuction(expectedAuctionContext, emptyList());
        verifyNoInteractions(storedResponseProcessor);
    }

    @Test
    public void skipAuctionShouldReturnBidResponseWithEmptySeatBidsWhenNoValueAvailableById() {
        // given
        final ExtStoredAuctionResponse givenStoredResponse = ExtStoredAuctionResponse.of("id", null);
        final AuctionContext givenAuctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .timeoutContext(TimeoutContext.of(1000L, timeout, 0))
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .storedAuctionResponse(givenStoredResponse)
                                .build()))
                        .build())
                .build();

        final BidResponse givenBidResponse = BidResponse.builder().build();
        given(bidResponseCreator.createOnSkippedAuction(any(), any()))
                .willReturn(Future.succeededFuture(givenBidResponse));
        given(storedResponseProcessor.getStoredResponseResult("id", timeout))
                .willReturn(Future.failedFuture("no value"));

        // when
        final Future<AuctionContext> result = target.skipAuction(givenAuctionContext);

        // then
        final AuctionContext expectedAuctionContext = givenAuctionContext.toBuilder()
                .debugWarnings(List.of(
                        "no value",
                        "no auction. response defined by storedauctionresponse"))
                .build();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(expectedAuctionContext.with(givenBidResponse).skipAuction());

        verify(bidResponseCreator).createOnSkippedAuction(expectedAuctionContext, Collections.emptyList());
    }

    @Test
    public void skipAuctionShouldReturnBidResponseWithStoredSeatBidsByProvidedId() {
        // given
        final List<SeatBid> givenSeatBids = givenSeatBids("bidId1", "bidId2");
        final ExtStoredAuctionResponse givenStoredResponse = ExtStoredAuctionResponse.of("id", null);
        final AuctionContext givenAuctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .timeoutContext(TimeoutContext.of(1000L, timeout, 0))
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .storedAuctionResponse(givenStoredResponse)
                                .build()))
                        .build())
                .build();

        final BidResponse givenBidResponse = BidResponse.builder().build();
        given(bidResponseCreator.createOnSkippedAuction(any(), any()))
                .willReturn(Future.succeededFuture(givenBidResponse));
        given(storedResponseProcessor.getStoredResponseResult("id", timeout))
                .willReturn(Future.succeededFuture(StoredResponseResult.of(null, givenSeatBids, null)));

        // when
        final Future<AuctionContext> result = target.skipAuction(givenAuctionContext);

        // then
        final AuctionContext expectedAuctionContext = givenAuctionContext.toBuilder()
                .debugWarnings(singletonList("no auction. response defined by storedauctionresponse"))
                .build();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(expectedAuctionContext.with(givenBidResponse).skipAuction());

        verify(bidResponseCreator).createOnSkippedAuction(expectedAuctionContext, givenSeatBids);
    }

    private static List<SeatBid> givenSeatBids(String... bidIds) {
        return Arrays.stream(bidIds)
                .map(bidId -> SeatBid.builder()
                        .seat("seat")
                        .bid(singletonList(Bid.builder().id(bidId).build()))
                        .build())
                .toList();

    }

}
