package org.prebid.server.bidder.openx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.openx.proto.OpenxRequestExt;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.openx.ExtImpOpenx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class OpenxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://openx.com/openrtb2d";

    private OpenxBidder openxBidder;

    @Before
    public void setUp() {
        openxBidder = new OpenxBidder(ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new OpenxBidder(null));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithEmptyBidRequestsAndErrors() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenAudioImpsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().id("impId1").audio(Audio.builder().build()).build(),
                        Imp.builder().id("impId2").audio(Audio.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .extracting(BidderError::getMessage)
                .containsExactly(
                        "OpenX only supports banner and video imps. Ignoring imp id=impId1",
                        "OpenX only supports banner and video imps. Ignoring imp id=impId2");
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenNativeImpsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().id("impId1").xNative(Native.builder().build()).build(),
                        Imp.builder().id("impId2").xNative(Native.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .extracting(BidderError::getMessage)
                .containsExactly(
                        "OpenX only supports banner and video imps. Ignoring imp id=impId1",
                        "OpenX only supports banner and video imps. Ignoring imp id=impId2");
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenImpExtOmitted() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("openx parameters section is missing");
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenImpExtMalformed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.createObjectNode())
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("openx parameters section is missing");
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenImpExtOpenxEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .video(Video.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, null))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("openx parameters section is missing");
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenImpExtOpenxMalformed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null,
                                                mapper.createArrayNode()))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("Cannot deserialize instance of");
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithExpectedFieldsSet() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("bidRequestId")
                .imp(asList(
                        Imp.builder()
                                .id("impId1")
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null,
                                                ExtImpOpenx.builder()
                                                        .customFloor(0.1f)
                                                        .customParams(singletonMap("foo1", "bar1"))
                                                        .delDomain("se-demo-d.openx.net")
                                                        .unit("unitId").build()))).build(),
                        Imp.builder()
                                .id("impId2")
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null,
                                                ExtImpOpenx.builder()
                                                        .customFloor(0.1f)
                                                        .customParams(singletonMap("foo2", "bar2"))
                                                        .delDomain("se-demo-d.openx.net")
                                                        .unit("unitId").build()))).build(),
                        Imp.builder()
                                .id("impId3")
                                .video(Video.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null,
                                                ExtImpOpenx.builder()
                                                        .customFloor(0.1f)
                                                        .customParams(singletonMap("foo3", "bar3"))
                                                        .delDomain("se-demo-d.openx.net")
                                                        .unit("unitId").build()))).build(),
                        Imp.builder()
                                .id("impId4")
                                .video(Video.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null,
                                                ExtImpOpenx.builder()
                                                        .customFloor(0.1f)
                                                        .customParams(singletonMap("foo4", "bar4"))
                                                        .delDomain("se-demo-d.openx.net")
                                                        .unit("unitId").build()))).build(),


                        Imp.builder().id("impId1").audio(Audio.builder().build()).build()))

                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("OpenX only supports banner and video imps. Ignoring imp id=impId1");

        assertThat(result.getValue()).hasSize(3)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(
                        // check if all banner imps are part of single bidRequest
                        BidRequest.builder()
                                .id("bidRequestId")
                                .imp(asList(
                                        Imp.builder()
                                                .id("impId1")
                                                .banner(Banner.builder().build())
                                                .tagid("unitId")
                                                .bidfloor(0.1f)
                                                .ext(mapper.valueToTree(
                                                        ExtImpOpenx.builder()
                                                                .customParams(
                                                                        singletonMap("foo1", "bar1"))
                                                                .build()))
                                                .build(),
                                        Imp.builder()
                                                .id("impId2")
                                                .banner(Banner.builder().build())
                                                .tagid("unitId")
                                                .bidfloor(0.1f)
                                                .ext(mapper.valueToTree(
                                                        ExtImpOpenx.builder()
                                                                .customParams(
                                                                        singletonMap("foo2", "bar2"))
                                                                .build()))
                                                .build()))
                                .ext(mapper.valueToTree(OpenxRequestExt.of("se-demo-d.openx.net", "hb_pbs_1.0.0")))
                                .build(),
                        // check if each of video imps is a part of separate bidRequest
                        BidRequest.builder()
                                .id("bidRequestId")
                                .imp(singletonList(
                                        Imp.builder()
                                                .id("impId3")
                                                .video(Video.builder().build())
                                                .tagid("unitId")
                                                .bidfloor(
                                                        0.1f)// check if each of video imps is a part of separate bidRequest
                                                .ext(mapper.valueToTree(
                                                        ExtImpOpenx.builder()
                                                                .customParams(
                                                                        singletonMap("foo3", "bar3"))
                                                                .build()))
                                                .build()))

                                .ext(mapper.valueToTree(OpenxRequestExt.of("se-demo-d.openx.net", "hb_pbs_1.0.0")))
                                .build(),
                        // check if each of video imps is a part of separate bidRequest
                        BidRequest.builder()
                                .id("bidRequestId")
                                .imp(singletonList(
                                        Imp.builder()
                                                .id("impId4")
                                                .video(Video.builder().build())
                                                .tagid("unitId")
                                                .bidfloor(0.1f)
                                                .ext(mapper.valueToTree(
                                                        ExtImpOpenx.builder()
                                                                .customParams(
                                                                        singletonMap("foo4", "bar4"))
                                                                .build()))
                                                .build()))
                                .ext(mapper.valueToTree(OpenxRequestExt.of("se-demo-d.openx.net", "hb_pbs_1.0.0")))
                                .build());
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfResponseStatusIs204() {
        // given
        final HttpCall httpCall = givenHttpCall(204, null);

        // when
        final Result<List<BidderBid>> result = openxBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseStatusIsNot200Or204() {
        // given
        final HttpCall httpCall = givenHttpCall(302, null);

        // when
        final Result<List<BidderBid>> result = openxBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsOnly("Unexpected status code: 302. Run with request.test = 1 for more info");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall httpCall = givenHttpCall(200, "invalid");

        // when
        final Result<List<BidderBid>> result = openxBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1).extracting(BidderError::getMessage).containsOnly(
                "Unrecognized token 'invalid': was expecting ('true', 'false' or 'null')\n" +
                        " at [Source: (String)\"invalid\"; line: 1, column: 15]");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnResultWithExpectedFields() throws JsonProcessingException {
        // given
        final HttpCall httpCall = givenHttpCall(200, mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .w(200)
                                .h(150)
                                .price(BigDecimal.ONE)
                                .impid("impId1")
                                .dealid("dealid")
                                .adm("<div>This is an Ad</div>")
                                .build()))
                        .build()))
                .build()));

        final BidRequest bidRequest = BidRequest.builder()
                .id("bidRequestId")
                .imp(singletonList(Imp.builder()
                        .id("impId1")
                        .banner(Banner.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<BidderBid>> result = openxBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(
                        Bid.builder()
                                .impid("impId1")
                                .price(BigDecimal.ONE)
                                .dealid("dealid")
                                .w(200)
                                .h(150)
                                .adm("<div>This is an Ad</div>")
                                .build(),
                        BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnRespectBannerImpWhenBothBannerAndVideoImpWithSameIdExist()
            throws JsonProcessingException {
        // given
        final HttpCall httpCall = givenHttpCall(200, mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .w(200)
                                .h(150)
                                .price(BigDecimal.ONE)
                                .impid("impId1")
                                .dealid("dealid")
                                .adm("<div>This is an Ad</div>")
                                .build()))
                        .build()))
                .build()));

        final BidRequest bidRequest = BidRequest.builder()
                .id("bidRequestId")
                .imp(singletonList(Imp.builder()
                        .id("impId1")
                        .video(Video.builder().build())
                        .banner(Banner.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<BidderBid>> result = openxBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(
                        Bid.builder()
                                .impid("impId1")
                                .price(BigDecimal.ONE)
                                .dealid("dealid")
                                .w(200)
                                .h(150)
                                .adm("<div>This is an Ad</div>")
                                .build(),
                        BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnResultContainingEmptyValueAndErrorsWhenSeatBidEmpty()
            throws JsonProcessingException {
        // given
        final HttpCall httpCall = givenHttpCall(200, mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = openxBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result).isNotNull()
                .extracting(Result::getValue, Result::getErrors)
                .containsOnly(Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void extractTargeting() {
        assertThat(openxBidder.extractTargeting(mapper.createObjectNode())).isEmpty();
    }

    private static HttpCall givenHttpCall(int statusCode, String body) {
        return HttpCall.full(null, HttpResponse.of(statusCode, null, body), null);
    }
}
