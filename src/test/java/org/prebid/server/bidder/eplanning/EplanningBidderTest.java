package org.prebid.server.bidder.eplanning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.eplanning.ExtImpEplanning;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class EplanningBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://eplanning.com";
    private static final String APPLICATION_JSON = HttpHeaderValues.APPLICATION_JSON.toString() + ";"
            + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private EplanningBidder eplanningBidder;

    @Before
    public void setUp() {
        eplanningBidder = new EplanningBidder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBodyHeadersAndMethod()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder().banner(Banner.builder().build()).ext(Json.mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpEplanning.of("exchangeId")))).build()))
                .device(Device.builder().ip("192.168.0.1").ua("ua").dnt(1).language("en").build())
                .user(User.builder().ext(Json.mapper.valueToTree(ExtUser.of(null, "consent", null))).build())
                .regs(Regs.of(0, Json.mapper.valueToTree(ExtRegs.of(1))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("http://eplanning.com/exchangeId");

        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpHeaders.CONTENT_TYPE.toString(), APPLICATION_JSON),
                        tuple(HttpHeaders.ACCEPT.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpHeaders.USER_AGENT.toString(), "ua"),
                        tuple(HttpHeaders.ACCEPT_LANGUAGE.toString(), "en"),
                        tuple("X-Forwarded-For", "192.168.0.1"),
                        tuple("DNT", "1"));

        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsExactly(Json.mapper.writeValueAsString(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpEplanning.of("exchangeId"))))
                                .build()))
                        .user(User.builder().ext(Json.mapper.valueToTree(ExtUser.of(null, "consent", null))).build())
                        .device(Device.builder().ip("192.168.0.1").ua("ua").dnt(1).language("en").build())
                        .regs(Regs.of(0, Json.mapper.valueToTree(ExtRegs.of(1))))
                        .build()
        ));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithoutDeviceHeadersIfDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder().banner(Banner.builder().build()).ext(Json.mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpEplanning.of("exchangeId")))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey)
                .containsOnly(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaders.ACCEPT.toString());
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithoutDeviceHeadersIfDeviceFieldsAreNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder().banner(Banner.builder().build()).ext(Json.mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpEplanning.of("exchangeId")))).build()))
                .device(Device.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey)
                .containsOnly(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaders.ACCEPT.toString());
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageWhenMediaTypeWasNotDefined() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badInput(
                "EPlanning only supports banner Imps. Ignoring Imp ID=impId"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageWhenImpExtBidderIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, null))).build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badInput(
                "Ignoring imp id=impId, error while decoding extImpBidder, err: bidder property is not present"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageWhenExtImpIsNotValidJson() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(Json.mapper.createObjectNode().put("bidder", 4)).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith(
                "Ignoring imp id=impId, error while decoding extImpBidder, err: Cannot construct instance of ");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithErrorMessage() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .id("impId")
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, null))).build(),
                        Imp.builder()
                                .id("impId2")
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpEplanning.of("exchangeId"))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badInput(
                "Ignoring imp id=impId, error while decoding extImpBidder, err: bidder property is not present"));
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("http://eplanning.com/exchangeId");
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> Json.mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getId).containsExactly("impId2");
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithDefaultExchangeIdInUrlIfMissedInExtBidder() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpEplanning.of(null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getUri)
                .containsExactly("http://eplanning.com/5a1ad71d2d53a0f5");
    }

    @Test
    public void makeHttpRequestsShouldReturnTwoHttpRequestsWhenTwoImpsHasDifferentExchangeIds() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpEplanning.of("exchangeId1"))))
                                .build(),
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpEplanning.of("exchangeId2"))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getUri)
                .containsExactly("http://eplanning.com/exchangeId2", "http://eplanning.com/exchangeId1");
    }

    @Test
    public void makeHttpRequestsShouldReturnOneHttpRequestForTowImpsWhenImpsHasSameSourceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpEplanning.of("exchangeId1"))))
                                .build(),
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpEplanning.of("exchangeId1"))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getUri)
                .containsExactly("http://eplanning.com/exchangeId1");
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

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = eplanningBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorListsIfSeatBidIsNotPresentInResponse()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().build());
        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = eplanningBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCantBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("{");

        // when
        final Result<List<BidderBid>> result = eplanningBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badServerResponse(
                "Failed to decode: Unexpected end-of-input: expected close marker for Object (start marker at " +
                        "[Source: (String)\"{\"; line: 1, column: 1])\n at [Source: (String)\"{\"; line: 1, column: " +
                        "3]"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        // given, when and then
        assertThat(eplanningBidder.extractTargeting(Json.mapper.createObjectNode())).hasSize(0);
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
