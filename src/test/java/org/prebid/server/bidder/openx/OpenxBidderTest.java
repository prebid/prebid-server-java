package org.prebid.server.bidder.openx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.openx.ExtImpOpenx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class OpenxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test/auction";

    private OpenxBidder openxBidder;

    @Before
    public void setUp() {
        openxBidder = new OpenxBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new OpenxBidder(null, jacksonMapper));
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
                .containsExactly(
                        BidderError.badInput("OpenX only supports banner and video imps. Ignoring imp id=impId1"),
                        BidderError.badInput(
                                "OpenX only supports banner and video imps. Ignoring imp id=impId2"));
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
                .containsExactly(
                        BidderError.badInput("OpenX only supports banner and video imps. Ignoring imp id=impId1"),
                        BidderError.badInput(
                                "OpenX only supports banner and video imps. Ignoring imp id=impId2"));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenImpExtOmitted() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("openx parameters section is missing"));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenImpExtMalformed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.createObjectNode())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("openx parameters section is missing"));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenImpExtOpenxEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("openx parameters section is missing"));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorWhenImpExtOpenxMalformed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
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
                                                        .customFloor(BigDecimal.valueOf(0.1))
                                                        .customParams(givenCustomParams("foo1", singletonList("bar1")))
                                                        .delDomain("se-demo-d.openx.net")
                                                        .unit("unitId").build()))).build(),
                        Imp.builder()
                                .id("impId2")
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null,
                                                ExtImpOpenx.builder()
                                                        .customFloor(BigDecimal.valueOf(0.1))
                                                        .customParams(givenCustomParams("foo2", "bar2"))
                                                        .delDomain("se-demo-d.openx.net")
                                                        .unit("unitId").build()))).build(),
                        Imp.builder()
                                .id("impId3")
                                .video(Video.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null,
                                                ExtImpOpenx.builder()
                                                        .customFloor(BigDecimal.valueOf(0.1))
                                                        .customParams(givenCustomParams("foo3", "bar3"))
                                                        .delDomain("se-demo-d.openx.net")
                                                        .unit("unitId").build()))).build(),
                        Imp.builder()
                                .id("impId4")
                                .video(Video.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null,
                                                ExtImpOpenx.builder()
                                                        .customFloor(BigDecimal.valueOf(0.1))
                                                        .customParams(givenCustomParams("foo4", "bar4"))
                                                        .delDomain("se-demo-d.openx.net")
                                                        .unit("unitId").build()))).build(),

                        Imp.builder().id("impId1").audio(Audio.builder().build()).build()))
                .user(User.builder().ext(mapper.valueToTree(ExtUser.builder().consent("consent").build())).build())
                .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput(
                        "OpenX only supports banner and video imps. Ignoring imp id=impId1"));

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
                                                .bidfloor(BigDecimal.valueOf(0.1))
                                                .ext(mapper.valueToTree(
                                                        ExtImpOpenx.builder()
                                                                .customParams(
                                                                        givenCustomParams("foo1",
                                                                                singletonList("bar1")))
                                                                .build()))
                                                .build(),
                                        Imp.builder()
                                                .id("impId2")
                                                .banner(Banner.builder().build())
                                                .tagid("unitId")
                                                .bidfloor(BigDecimal.valueOf(0.1))
                                                .ext(mapper.valueToTree(
                                                        ExtImpOpenx.builder()
                                                                .customParams(
                                                                        givenCustomParams("foo2", "bar2"))
                                                                .build()))
                                                .build()))
                                .ext(mapper.valueToTree(OpenxRequestExt.of("se-demo-d.openx.net", "hb_pbs_1.0.0")))
                                .user(User.builder()
                                        .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                                        .build())
                                .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null))))
                                .build(),
                        // check if each of video imps is a part of separate bidRequest
                        BidRequest.builder()
                                .id("bidRequestId")
                                .imp(singletonList(
                                        Imp.builder()
                                                .id("impId3")
                                                .video(Video.builder().build())
                                                .tagid("unitId")
                                                // check if each of video imps is a part of separate bidRequest
                                                .bidfloor(BigDecimal.valueOf(0.1))
                                                .ext(mapper.valueToTree(
                                                        ExtImpOpenx.builder()
                                                                .customParams(
                                                                        givenCustomParams("foo3", "bar3"))
                                                                .build()))
                                                .build()))

                                .ext(mapper.valueToTree(OpenxRequestExt.of("se-demo-d.openx.net", "hb_pbs_1.0.0")))
                                .user(User.builder()
                                        .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                                        .build())
                                .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null))))
                                .build(),
                        // check if each of video imps is a part of separate bidRequest
                        BidRequest.builder()
                                .id("bidRequestId")
                                .imp(singletonList(
                                        Imp.builder()
                                                .id("impId4")
                                                .video(Video.builder().build())
                                                .tagid("unitId")
                                                .bidfloor(BigDecimal.valueOf(0.1))
                                                .ext(mapper.valueToTree(
                                                        ExtImpOpenx.builder()
                                                                .customParams(
                                                                        givenCustomParams("foo4", "bar4"))
                                                                .build()))
                                                .build()))
                                .ext(mapper.valueToTree(OpenxRequestExt.of("se-demo-d.openx.net", "hb_pbs_1.0.0")))
                                .user(User.builder()
                                        .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                                        .build())
                                .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null))))
                                .build());
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = openxBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                        && error.getMessage().startsWith("Failed to decode: Unrecognized token 'invalid'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnResultWithExpectedFields() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

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

    private static Map<String, JsonNode> givenCustomParams(String key, Object values) {
        return singletonMap(key, mapper.valueToTree(values));
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
