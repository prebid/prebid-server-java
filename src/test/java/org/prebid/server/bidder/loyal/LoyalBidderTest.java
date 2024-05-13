package org.prebid.server.bidder.loyal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.between.ExtImpBetween;
import org.prebid.server.proto.openrtb.ext.request.loyal.ExtImpLoyal;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class LoyalBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test?param={{PlacementId}}&param2={{EndpointId}}";

    private final LoyalBidder target = new LoyalBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LoyalBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBody() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue())
                .hasSize(1)
                .first().satisfies(request -> assertThat(request.getBody()).isEqualTo(jacksonMapper.encodeToBytes(bidRequest)))
                .satisfies(request -> assertThat(request.getPayload()).isEqualTo(bidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing bidder ext in impression with id: 123"));
    }

    @Test
    public void shouldReplacePlacementIdMacro() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpLoyal.of("placement123", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("https://test.com/test?param=placement123");
    }

    @Test
    public void shouldReplaceEndpointIdMacro() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpLoyal.of(null, "endpoint123")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("https://test.com/test?param2=endpoint123");
    }

    @Test
    public void shouldHandleErrorResponse() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpLoyal.of("placement123", "endpointId")))));
        final HttpRequest<BidRequest> httpRequest = HttpRequest.<BidRequest>builder().method(HttpMethod.POST).uri("https://test.com/test?param=placement123").body(jacksonMapper.encodeToBytes(bidRequest)).headers(HttpUtil.headers()).payload(bidRequest).build();
        final BidderCall<BidRequest> httpCall = BidderCall.failedHttp(httpRequest, BidderError.badInput("Bad request"));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage()).isEqualTo("No response or empty body");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
        });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldHandleNullBidResponse() {
        // given
        final HttpRequest<BidRequest> httpRequest = HttpRequest.<BidRequest>builder().method(HttpMethod.POST).uri("https://test.com").body(new byte[0]).build();
        final HttpResponse httpResponse = HttpResponse.of(200, null, "{}");
        final BidderCall<BidRequest> httpCall = BidderCall.storedHttp(httpRequest, httpResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage()).isEqualTo("Empty SeatBid array");
    }

    @Test
    public void makeBidsShouldHandleEmptySeatbid() {
        // given
        final HttpRequest<BidRequest> httpRequest = HttpRequest.<BidRequest>builder().method(HttpMethod.POST).uri("https://test.com").body(new byte[0]).build();
        final HttpResponse httpResponse = HttpResponse.of(200, null, "{\"seatbid\": []}");
        final BidderCall<BidRequest> httpCall = BidderCall.storedHttp(httpRequest, httpResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage()).isEqualTo("Empty SeatBid array");
    }

    @Test
    public void makeBidsShouldReturnAllFourBidTypesSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).build();
        final Bid videoBid = Bid.builder().impid("2").mtype(2).build();
        final Bid audioBid = Bid.builder().impid("3").mtype(3).build();
        final Bid nativeBid = Bid.builder().impid("4").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(bannerBid, videoBid, audioBid, nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, banner, "USD"), BidderBid.of(videoBid, video, "USD"), BidderBid.of(audioBid, audio, "USD"), BidderBid.of(nativeBid, xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(bannerBid));

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

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(audioBid));

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

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(videoBid));
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

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(nativeBid));

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Unable to fetch mediaType 5 in multi-format: id"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpTypeIsNull() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().impid("id").mtype(null).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Missing MType for bid: " + bid.getId()));
    }

    private static BidRequest givenBidRequest() {
        final Imp imp = Imp.builder().id("imp_id").ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpLoyal.of("{{PlacementId}}", "endpointId")))).build();
        return BidRequest.builder().imp(List.of(imp)).build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder().cur("USD").seatbid(bids.length == 0 ? Collections.emptyList() : List.of(SeatBid.builder().bid(List.of(bids)).build())).build());
    }

    private static BidRequest givenBidRequest(Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer, Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder().imp(singletonList(givenImp(impCustomizer)))).build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("123").banner(Banner.builder().w(23).h(25).build()).ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBetween.of(null, "pubId"))))).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(HttpRequest.<BidRequest>builder().payload(bidRequest).build(), HttpResponse.of(200, null, body), null);
    }
}
