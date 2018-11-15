package org.prebid.server.bidder.somoaudience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.somoaudience.proto.SomoaudienceReqExt;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.somoaudience.ExtImpSomoaudience;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class SomoaudienceBidderTest extends VertxTest {

    private static final String APPLICATION_JSON = HttpHeaderValues.APPLICATION_JSON.toString() + ";"
            + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";
    private static final String ENDPOINT_URL = "http://somoaudience.com";

    private SomoaudienceBidder somoaudienceBidder;

    @Before
    public void setUp() {
        somoaudienceBidder = new SomoaudienceBidder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBodyHeadersAndMethod()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSomoaudience.of("placementId", null))))
                        .build()))
                .user(User.builder().ext(mapper.valueToTree(ExtUser.of(null, "consent", null))).build())
                .device(Device.builder().ua("User Agent").ip("ip").dnt(1).language("en").build())
                .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("http://somoaudience.com?s=placementId");
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpHeaders.CONTENT_TYPE.toString(), APPLICATION_JSON),
                        tuple(HttpHeaders.ACCEPT.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple("x-openrtb-version", "2.5"),
                        tuple("User-Agent", "User Agent"),
                        tuple("X-Forwarded-For", "ip"),
                        tuple("Accept-Language", "en"),
                        tuple("DNT", "1"));
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsExactly(mapper.writeValueAsString(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                                .build()))
                        .user(User.builder().ext(mapper.valueToTree(ExtUser.of(null, "consent", null))).build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                        .ext(mapper.valueToTree(SomoaudienceReqExt.of("hb_pbs_1.0.0")))
                        .device(Device.builder().ua("User Agent").ip("ip").dnt(1).language("en").build())
                        .build()
        ));
    }

    @Test
    public void makeHttpRequestsShouldReturnCorrectRequestBodyAndUri() throws IOException {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(asList(
                Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null, ExtImpSomoaudience.of("placement1", BigDecimal.valueOf(1.5)))))
                        .build(),
                Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null, ExtImpSomoaudience.of("placement2", BigDecimal.valueOf(1.33)))))
                        .build(),
                Imp.builder()
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSomoaudience.of("placement3", null))))
                        .build(),
                Imp.builder()
                        .xNative(Native.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSomoaudience.of("placement4", null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedBannersString = mapper.writeValueAsString(BidRequest.builder()
                .imp(asList(
                        Imp.builder().banner(Banner.builder().build()).bidfloor(1.5F).build(),
                        Imp.builder().banner(Banner.builder().build()).bidfloor(1.33F).build()))
                .ext(mapper.valueToTree(SomoaudienceReqExt.of("hb_pbs_1.0.0")))
                .build());
        final String expectedVideoSting = mapper.writeValueAsString(BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build()).build()))
                .ext(mapper.valueToTree(SomoaudienceReqExt.of("hb_pbs_1.0.0")))
                .build());
        final String expectedNativeString = mapper.writeValueAsString(BidRequest.builder()
                .imp(singletonList(Imp.builder().xNative(Native.builder().build()).build()))
                .ext(mapper.valueToTree(SomoaudienceReqExt.of("hb_pbs_1.0.0")))
                .build());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(3).element(0)
                .returns("http://somoaudience.com?s=placement2", HttpRequest::getUri)
                .returns(expectedBannersString, HttpRequest::getBody);
        assertThat(result.getValue()).element(1)
                .returns("http://somoaudience.com?s=placement3", HttpRequest::getUri)
                .returns(expectedVideoSting, HttpRequest::getBody);
        assertThat(result.getValue()).element(2)
                .returns("http://somoaudience.com?s=placement4", HttpRequest::getUri)
                .returns(expectedNativeString, HttpRequest::getBody);
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenMediaTypeIsAudio() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .audio(Audio.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSomoaudience.of("placementId", null)))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactlyInAnyOrder(BidderError.badInput(
                        "SomoAudience only supports banner and video imps. Ignoring imp id=impId"));
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
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("ignoring imp id=impId, extImpBidder is empty"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnHttpRequestWithErrorMessage() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .id("impId")
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, null))).build(),
                        Imp.builder()
                                .id("impId2")
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpSomoaudience.of("placementId", null))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("ignoring imp id=impId, extImpBidder is empty"));
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("http://somoaudience.com?s=placementId");
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getId).containsExactly("impId2");
    }

    @Test
    public void makeBidsShouldReturnBidWithoutErrors() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId")
                .banner(Banner.builder().build()).build()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldTakeBannerPrecedencyOverAllOtherMediaTypes() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId")
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .xNative(Native.builder().build()).build()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldTakeVideoPrecedencyOverNative() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId")
                .video(Video.builder().build())
                .xNative(Native.builder().build()).build()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.video, null));
    }

    @Test
    public void makeBidsShouldReturnBidsWithNativeTypeOnlyWhenAllOtherAreAbsent() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId")
                .xNative(Native.builder().build()).build()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.xNative, null));
    }

    @Test
    public void makeBidsShouldReturnBidsWithMediaBidTypeIfMediaTypeWasNotDefinedInImp() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId").build())).build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnBidWithMediaBidTypeIfCorrespondentImpWasNotFound() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId2").build())).build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, null));
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

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId).containsExactly("bidId1", "bidId2");
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorListsIfSeatBidIsNotPresentInResponse()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().build());
        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCantBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("{");

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse(
                        "Failed to decode: Unexpected end-of-input: expected close marker for Object (start marker at" +
                                " [Source: (String)\"{\"; line: 1, column: 1])\n at [Source: (String)\"{\"; line: 1, " +
                                "column: 3]"));
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
