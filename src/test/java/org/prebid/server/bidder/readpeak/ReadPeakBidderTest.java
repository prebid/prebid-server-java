package org.prebid.server.bidder.readpeak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
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
import org.prebid.server.proto.openrtb.ext.request.readpeak.ExtImpReadPeak;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class ReadPeakBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test?param={{PublisherId}}";

    private final ReadPeakBidder target = new ReadPeakBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl2() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReadPeakBidder("invalid_url", jacksonMapper));
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
                .first().satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(bidRequest)))
                .satisfies(request -> assertThat(request.getPayload()).isEqualTo(bidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds()).isEqualTo(Set.of("123")));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .allSatisfy(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasOnlyInvalidImpression() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Invalid Imp ext: ");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                }
        );
    }

    @Test
    public void shouldReplacePlacementIdMacro() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpReadPeak
                                .of("placement123", null, null, null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("https://test.com/test?param=placement123");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).allSatisfy(error -> {
            assertThat(error.getMessage()).startsWith("Failed to decode");
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
        });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseDoesNotHaveSeatBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, givenBidResponse());

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("Empty SeatBid array"));
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
    public void makeBidsShouldReturnAllTwoBidTypesSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).price(BigDecimal.TEN).build();
        final Bid nativeBid = Bid.builder().impid("2").mtype(2).price(BigDecimal.TEN).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(),
                givenBidResponse(bannerBid, nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(bannerBid, banner, "USD"), BidderBid.of(nativeBid, xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).price(BigDecimal.TEN).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(bannerBid, banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid nativeBid = Bid.builder().impid("2").mtype(2).price(BigDecimal.TEN).build();

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
        final Bid nativeBid = Bid.builder().impid("id").mtype(3).price(BigDecimal.TEN).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Unable to fetch mediaType 3 in multi-format: id"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpTypeIsNull() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().impid("id").mtype(null).price(BigDecimal.TEN).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Missing MType for bid: " + bid.getId()));
    }

    @Test
    public void makeBidsShouldReturnBidWithResolvedMacros() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder
                        .impid("1")
                        .nurl("https://mynurl.example.com/win/232332?price=${AUCTION_PRICE}")
                        .adm("<html><head></head><body><p><img"
                                + " src=\"https://myurl.example.com/imp?apr=${AUCTION_PRICE}\"/></p></body></head>")
                        .price(BigDecimal.valueOf(13.45)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getNurl, Bid::getAdm)
                .containsExactly(tuple("https://mynurl.example.com/win/232332?price=13.45",
                        "<html><head></head><body><p><img src=\"https://myurl.example.com/imp?apr=13.45\"/></p></body></head>"));
    }

//    @Test
//    public void creationShouldFailOnInvalidEndpointUrl() {
//        assertThatIllegalArgumentException().isThrownBy(() -> new ReadPeakBidder("invalid_url", jacksonMapper));
//    }
//
//    @Test
//    public void makeHttpRequestsShouldReturnErrorIfImpExtCannotBeParsed() {
//        // given
//        final BidRequest bidRequest = BidRequest.builder()
//                .imp(Collections.singletonList(Imp.builder()
//                        .ext(mapper.createObjectNode())
//                        .build()))
//                .build();
//
//        // when
//        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
//
//        // then
//        assertThat(result.getErrors()).hasSize(1)
//                .element(0)
//                .extracting(BidderError::getType)
//                .isEqualTo(BidderError.Type.bad_input);
//    }

//    @Test
//    public void makeHttpRequestsShouldReturnErrorIfExtImpReadPeakCannotBeParsed() {
//        // given
//        final ObjectNode ext = mapper.createObjectNode();
//        ext.put("bidder", "invalid");
//
//        final BidRequest bidRequest = BidRequest.builder()
//                .imp(Collections.singletonList(Imp.builder()
//                        .ext(ext)
//                        .build()))
//                .build();
//
//        // when
//        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
//
//        // then
//        assertThat(result.getErrors()).hasSize(1)
//                .element(0)
//                .extracting(BidderError::getType)
//                .isEqualTo(BidderError.Type.bad_input);
//    }
//
//    @Test
//    public void makeHttpRequestsShouldReturnHttpRequest() {
//        // given
//        final ExtImpReadPeak extImpReadPeak = ExtImpReadPeak.of("testPublisherId", "testSiteId", BigDecimal.valueOf(1.5), "testTagId");
//        final ObjectNode ext = mapper.createObjectNode();
//        ext.set("bidder", mapper.valueToTree(extImpReadPeak));
//
//        final BidRequest bidRequest = BidRequest.builder()
//                .imp(Collections.singletonList(Imp.builder()
//                        .id("123")
//                        .ext(ext)
//                        .build()))
//                .build();
//
//        // when
//        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
//
//        // then
//        assertThat(result.getErrors()).isEmpty();
//        assertThat(result.getValue()).hasSize(1)
//                .element(0)
//                .extracting(HttpRequest::getBody)
//                .isNotNull();
//    }
//
//    @Test
//    public void makeBidsShouldReturnEmptyResultIfResponseBodyIsEmpty() {
//        // given
//        final BidderCall<BidRequest> bidderCall = BidderCall.succeededHttp(null, HttpResponse.of(200, null, ""), null);
//
//        // when
//        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);
//
//        // then
//        assertThat(result.getErrors()).isEmpty();
//        assertThat(result.getValue()).isEmpty();
//    }
//
//    @Test
//    public void makeBidsShouldReturnErrorIfResponseBodyIsInvalid() {
//        // given
//        final HttpResponse httpResponse = HttpResponse.of(200, null, "invalid");
//        final BidderCall<BidRequest> bidderCall = BidderCall.succeededHttp(null, httpResponse, null);
//
//        // when
//        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);
//
//        // then
//        assertThat(result.getErrors()).hasSize(1)
//                .element(0)
//                .extracting(BidderError::getType)
//                .isEqualTo(BidderError.Type.bad_server_response);
//    }
//
//    @Test
//    public void makeBidsShouldReturnBids() throws Exception {
//        // given
//        final BidRequest bidRequest = BidRequest.builder().build();
//        final BidResponse bidResponse = BidResponse.builder()
//                .seatbid(Collections.singletonList(SeatBid.builder()
//                        .bid(Collections.singletonList(com.iab.openrtb.response.Bid.builder().mtype(1).build()))
//                        .build()))
//                .build();
//
//        final String bidResponseBody = mapper.writeValueAsString(bidResponse);
//        final HttpResponse httpResponse = HttpResponse.of(200, null, bidResponseBody);
//        final BidderCall<BidRequest> bidderCall = BidderCall.succeededHttp(null, httpResponse, null);
//
//        // when
//        final Result<List<BidderBid>> result = target.makeBids(bidderCall, bidRequest);
//
//        // then
//        assertThat(result.getErrors()).isEmpty();
//        assertThat(result.getValue()).hasSize(1);
//    }

    private static BidRequest givenBidRequest() {
        final Imp imp = Imp.builder()
                .id("imp_id")
                .tagid("TagId") // Dodaj tagid
                .bidfloor(BigDecimal.valueOf(0.5)) // Dodaj bidfloor
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpReadPeak
                                .of("PublisherId", "SiteId", BigDecimal.valueOf(0.5), "TagId")))).build();
        return BidRequest.builder().imp(List.of(imp)).build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .device(Device.builder().ua("ua").ip("ip").ipv6("ipv6").build())
                .imp(Arrays.stream(impCustomizers).map(ReadPeakBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("123").banner(Banner.builder().w(23).h(25).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpReadPeak
                        .of("publisherId", null, null, null))))).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
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
}
