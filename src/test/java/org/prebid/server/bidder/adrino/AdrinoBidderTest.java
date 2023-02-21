package org.prebid.server.bidder.adrino;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adtarget.proto.AdtargetImpExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adrino.ExtImpAdrino;
import org.prebid.server.proto.openrtb.ext.request.adtarget.ExtImpAdtarget;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.HttpUtil;


import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AdrinoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://prd-prebid-bidder.adrino.io/openrtb/bid";

    private AdrinoBidder adrinoBidder;

    @Before
    public void setUp() {
        adrinoBidder = new AdrinoBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBodyHeadersAndMethod() {
        // given
        // Build the Imp object
        Imp imp = Imp.builder()
                .xNative(Native.builder().request("{json string}").ver("1.2").build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdrino.of("123456")))).build();

        // Build the Site object
        Site site = Site.builder()
                .publisher(Publisher.builder().id("1").build())
                .page("some-page-url")
                .build();

        // Build the Device object
        Device device = Device.builder()
                .w(1920)
                .h(800)
                .build();

        // Build the BidRequest object
        BidRequest bidRequest = BidRequest.builder()
                .id("test-request-id")
                .imp(singletonList(imp))
                .site(site)
                .device(device)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adrinoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("https://prd-prebid-bidder.adrino.io/openrtb/bid");
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenNativeTypeWasNotDefined() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdrino.of("123456")))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adrinoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput(
                        "Ignoring imp id=impId, Adrino supports only Native"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenImpExtIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, null))).build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = adrinoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Ignoring imp id=impId, extImpBidder is empty"));
        assertThat(result.getValue()).isEmpty();
    }


    @Test
    public void makeHttpRequestShouldReturnSingleHttpRequestsWhenTwoImpsHasDifferentSourceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .xNative(Native.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdrino.of("test"))))
                                .build(),
                        Imp.builder()
                                .xNative(Native.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdrino.of("test"))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adrinoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestShouldReturnOneHttpRequestForTowImpsWhenImpsHasSameSourceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .xNative(Native.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdrino.of("test"))))
                                .build(),
                        Imp.builder()
                                .xNative(Native.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdrino.of("test"))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adrinoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestShouldReturnErrorWhenExtHashIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .xNative(Native.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdrino.of(null))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adrinoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput("Hash field required for bidder"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidWithoutErrors() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .cur("PLN")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().id("impId").impid("test-imp-id").price(BigDecimal.TEN)
                                .adm("{json response string}")
                                .crid("test-creative-id")
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(ExtBidPrebid.builder().type(BidType.xNative).build(), null)))
                                .mtype(4)
                                .build()))
                        .build()))
                .build());
        BidRequest bidRequest = BidRequest.builder()
                .id("test-request-id")
                .imp(singletonList(Imp.builder()
                        .id("test-imp-id")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdrino.of("123456"))))
                        .xNative(Native.builder()
                                .request("{json string}")
                                .ver("1.2")
                                .build())
                        .build()))
                .site(Site.builder()
                        .publisher(Publisher.builder()
                                .id("1")
                                .build())
                        .page("some-page-url")
                        .build())
                .device(Device.builder()
                        .w(1920)
                        .h(800)
                        .build())
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = adrinoBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().id("impId").impid("test-imp-id").price(BigDecimal.TEN).adm("{json response string}").ext(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.builder().type(BidType.xNative).build(), null))).crid("test-creative-id").mtype(4).build(), BidType.xNative, "PLN"));
    }

    @Test
    public void makeBidsShouldReturnEmptyResponseWhenWhereIsNoBid() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(null);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = adrinoBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
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
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").build(), Imp.builder().id("impId2").build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = adrinoBidder.makeBids(httpCall, bidRequest);

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
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = adrinoBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType).containsExactly(BidType.xNative);
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorListsIfSeatBidIsNotPresentInResponse()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = adrinoBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCantBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("{");

        // when
        final Result<List<BidderBid>> result = adrinoBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse(
                        "Failed to decode: Unexpected end-of-input: expected close marker for Object (start marker at"
                                + " [Source: (String)\"{\"; line: 1, column: 1])\n at [Source: (String)\"{\"; line: 1, "
                                + "column: 2]"));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(204, null, body), null);
    }
}
