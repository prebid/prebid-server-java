package org.prebid.server.bidder.adtelligent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adtelligent.proto.AdtelligentImpExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adtelligent.ExtImpAdtelligent;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AdtelligentBidderTest extends VertxTest {

    private static final String APPLICATION_JSON = HttpHeaderValues.APPLICATION_JSON.toString() + ";"
            + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";
    private static final String ENDPOINT_URL = "http://adtelligent.com";


    private AdtelligentBidder adtelligentBidder;

    @Before
    public void setUp() {
        adtelligentBidder = new AdtelligentBidder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBodyHeadersAndMethod()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtelligent.of(15, 1, 2, 3f)))).build()))
                .user(User.builder().ext(Json.mapper.valueToTree(ExtUser.of(null, "consent", null))).build())
                .regs(Regs.of(0, Json.mapper.valueToTree(ExtRegs.of(1))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtelligentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("http://adtelligent.com?aid=15");
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpHeaders.CONTENT_TYPE.toString(), APPLICATION_JSON),
                        tuple(HttpHeaders.ACCEPT.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsExactly(Json.mapper.writeValueAsString(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().build()).bidfloor(3f)
                                .ext(Json.mapper.valueToTree(AdtelligentImpExt.of(ExtImpAdtelligent.of(15, 1, 2, 3f))))
                                .build()))
                        .user(User.builder().ext(Json.mapper.valueToTree(ExtUser.of(null, "consent", null))).build())
                        .regs(Regs.of(0, Json.mapper.valueToTree(ExtRegs.of(1))))
                        .build()
        ));
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenMediaTypeWasNotDefined() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtelligent.of(15, 1, 2, 3f)))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtelligentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.createBadInput(
                        "ignoring imp id=impId, Adtelligent supports only Video and Banner"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenImpExtIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, null))).build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtelligentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.createBadInput("ignoring imp id=impId, extImpBidder is empty"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnHttpRequestWithErrorMessage() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .id("impId")
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, null))).build(),
                        Imp.builder()
                                .id("impId2")
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtelligent.of(15, 1, 2, 3f))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtelligentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.createBadInput("ignoring imp id=impId, extImpBidder is empty"));
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("http://adtelligent.com?aid=15");
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> Json.mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getId).containsExactly("impId2");
    }

    @Test
    public void makeHttpRequestShouldReturnWithBidFloorPopulatedFromImpWhenIsMissedInImpExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .bidfloor(16f)
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtelligent.of(15, 1, 2, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtelligentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getBidfloor).containsExactly(16f);
    }

    @Test
    public void makeHttpRequestShouldReturnTwoHttpRequestsWhenTwoImpsHasDifferentSourceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtelligent.of(15, 1, 2, 3f))))
                                .build(),
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtelligent.of(16, 1, 2, 3f))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtelligentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
    }

    @Test
    public void makeHttpRequestShouldReturnOneHttpRequestForTowImpsWhenImpsHasSameSourceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtelligent.of(15, 1, 2, 3f))))
                                .build(),
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtelligent.of(15, 1, 2, 3f))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtelligentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldReturnBidWithoutErrors() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.OK.code(), response);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnErrorMessageWhenMatchingToBidImpWasNotFound() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().id("bidId").impid("invalidId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.OK.code(), response);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.createBadServerResponse(
                        "ignoring bid id=bidId, request doesn't contain any impression with id=invalidId"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBadInputErrorIfResponseStatusIsBadRequest400() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(HttpResponseStatus.BAD_REQUEST.code(), null);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsOnly(BidderError.createBadInput(
                        "Unexpected status code: 400. Run with request.test = 1 for more info"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidWithErrorMessage() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(asList(Bid.builder().id("bidId1").impid("invalidId").build(),
                                Bid.builder().id("bidId2").impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.OK.code(), response);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.createBadServerResponse(
                        "ignoring bid id=bidId1, request doesn't contain any impression with id=invalidId"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId).containsExactly("bidId2");
    }

    @Test
    public void makeBidsShouldReturnBidsFromDifferentSeatBidsInResponse() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(asList(
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId1").impid("impId1").build()))
                                .build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId2").impid("impId2").build()))
                                .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(asList(Imp.builder().id("impId1").build(),
                Imp.builder().id("impId2").build())).build();

        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.OK.code(), response);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId).containsExactly("bidId1", "bidId2");
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithBannerBidTypeWhenMediaTypeInMatchedImpIsNotVideo()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.OK.code(), response);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType).containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithVideoBidTypeIfBannerAndVideoMediaTypesAreInMatchedImp()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(
                Imp.builder().video(Video.builder().build()).banner(Banner.builder().build()).id("impId").build()))
                .build();

        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.OK.code(), response);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType).containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorListsIfResponseStatusIsNoContent() {
        // given
        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.NO_CONTENT.code(), null);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorMessageIsResponseStatusIsNotOk() {
        // given
        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), null);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.createBadServerResponse(
                        "Unexpected status code: 500. Run with request.test = 1 for more info"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorListsIfSeatBidIsNotPresentInResponse()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().build());
        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.OK.code(), response);

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCantBeParsed() {
        // given
        final HttpCall httpCall = givenHttpCall(HttpResponseStatus.OK.code(), "{");

        // when
        final Result<List<BidderBid>> result = adtelligentBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.createBadServerResponse(
                        "Unexpected end-of-input: expected close marker for Object (start marker at [Source:" +
                                " (String)\"{\"; line: 1, column: 1])\n at [Source: (String)\"{\"; line: 1, column: 3]"));
    }

    private static HttpCall givenHttpCall(int statusCode, String body) {
        return HttpCall.full(null, HttpResponse.of(statusCode, null, body), null);
    }
}