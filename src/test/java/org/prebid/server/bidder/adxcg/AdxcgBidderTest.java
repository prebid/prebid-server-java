package org.prebid.server.bidder.adxcg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adxcg.ExtImpAdxcg;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AdxcgBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.com/";

    private final AdxcgBidder target = new AdxcgBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdxcgBidder("invalid", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdxcg.of("adZoneId"))))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, givenBidResponse());

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

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(bannerBid, banner, "USD"));

    }

    @Test
    public void makeBidsShouldReturnAudioBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid audioBid = Bid.builder().impid("3").mtype(3).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(audioBid, audio, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid videoBid = Bid.builder().impid("2").mtype(2).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(videoBid));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(videoBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid nativeBid = Bid.builder().impid("4").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(nativeBid, xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpTypeIsNotSupported() throws JsonProcessingException {
        // given
        final Bid audioBid = Bid.builder().impid("id").mtype(5).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Unable to fetch mediaType 5 in multi-format: id"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidAndErrorIfNativeAndEmptyImpsArePresent() throws JsonProcessingException {
        // given
        final Bid invalidBid = Bid.builder().impid("id1").mtype(5).build();
        final Bid validBid = Bid.builder().impid("id2").mtype(1).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(invalidBid, validBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Unable to fetch mediaType 5 in multi-format: id1"));
        assertThat(result.getValue()).containsOnly(BidderBid.of(validBid, banner, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldCreateOneRequestForAllImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .id("123")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(300).h(200).build())).build())),
                        givenImp(impBuilder -> impBuilder
                                .id("321")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(600).h(400).build())).build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsOnly("123", "321");
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body), null);
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(
                BidResponse.builder()
                        .cur("USD")
                        .seatbid(bids.length == 0
                                ? Collections.emptyList()
                                : List.of(SeatBid.builder().bid(List.of(bids)).build()))
                        .build());
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().id("banner_id").build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdxcg.of("adzoneid")))))
                .build();
    }
}
