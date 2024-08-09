package org.prebid.server.bidder.vidazoo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.vidazoo.VidazooImpExt;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.Type;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

class VidazooBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.host.com/prebid/bid/";

    private final VidazooBidder target = new VidazooBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VidazooBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final Imp givenImp1 = givenImp(imp -> imp.id("givenImp1"));
        final Imp givenImp2 = givenImp(imp -> imp.id("givenImp2"));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(givenImp1, givenImp2)).build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactlyInAnyOrder(givenImp1, givenImp2);
    }

    @Test
    public void makeHttpRequestsShouldHaveImpIds() {
        // given
        final Imp givenImp1 = givenImp(imp -> imp.id("givenImp1"));
        final Imp givenImp2 = givenImp(imp -> imp.id("givenImp2"));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(givenImp1, givenImp2)).build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder(Collections.singleton("givenImp1"), Collections.singleton("givenImp2"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void shouldMakeOneRequestWhenOneImpIsValidAndAnotherIsNot() {
        // given
        final Imp givenInvalidImp = givenImp(imp -> imp
                .id("impIdCorrupted")
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        final Imp givenValidImp = givenImp(identity());

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenInvalidImp, givenValidImp))
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("impId");
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.host.com/prebid/bid/cid");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(bannerBid, banner, "USD"));

    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid videoBid = Bid.builder().impid("2").mtype(2).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(videoBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpTypeIsNotSupported() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().id("bidId1").impid("id1").mtype(1).build();
        final Bid bidWithoutMtype = Bid.builder().id("bidId2").impid("id2").mtype(null).build();
        final Bid audioBid = Bid.builder().id("bidId3").impid("id3").mtype(3).build();
        final Bid nativeBid = Bid.builder().id("bidId4").impid("id4").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bannerBid, bidWithoutMtype, audioBid, nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).containsExactlyInAnyOrder(
                badServerResponse("Missing MType for bid: bidId2"),
                badServerResponse("Could not define bid type for imp: id3"),
                badServerResponse("Could not define bid type for imp: id4"));
        assertThat(result.getValue()).containsOnly(BidderBid.of(bannerBid, banner, "USD"));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(singletonList(givenImp(impCustomizer)))).build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, VidazooImpExt.of("cid")))))
                .build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
