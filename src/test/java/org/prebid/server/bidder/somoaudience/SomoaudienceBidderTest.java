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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.somoaudience.ExtImpSomoaudience;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class SomoaudienceBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://somoaudience.com";

    private SomoaudienceBidder somoaudienceBidder;

    @Before
    public void setUp() {
        somoaudienceBidder = new SomoaudienceBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBodyHeadersAndMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("http://somoaudience.com?s=placementId");
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "User Agent"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "ip"),
                        tuple(HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(), "en"),
                        tuple(HttpUtil.DNT_HEADER.toString(), "1"));
        assertThat(result.getValue()).extracting(HttpRequest::getPayload).containsExactly(
                givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                                .ext(jacksonMapper.fillExtension(ExtRequest.empty(),
                                        SomoaudienceReqExt.of("hb_pbs_1.0.0"))),
                        impBuilder -> impBuilder.ext(null).bidfloor(BigDecimal.valueOf(1.39))));
    }

    @Test
    public void makeHttpRequestsShouldAddOnlyDeviceNotEmptyValuesToHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.device(Device.builder()
                        .ua("User Agent")
                        .ip("")
                        .dnt(1)
                        .language(null)
                        .build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey)
                .doesNotContain(HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(),
                        HttpUtil.X_FORWARDED_FOR_HEADER.toString());
    }

    @Test
    public void makeHttpRequestsShouldReturnCorrectRequestBodyAndUri() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(asList(
                givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(
                        null, ExtImpSomoaudience.of("placement1", BigDecimal.valueOf(1.54)))))),
                givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(
                        null, ExtImpSomoaudience.of("placement2", BigDecimal.valueOf(1.33)))))),
                givenImp(impBuilder -> impBuilder
                        .video(Video.builder().build())
                        .banner(null)
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null, ExtImpSomoaudience.of("placement3", BigDecimal.valueOf(1.97)))))),

                givenImp(impBuilder -> impBuilder
                        .xNative(Native.builder().build())
                        .banner(null)
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null, ExtImpSomoaudience.of("placement4", BigDecimal.valueOf(2.52))))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedBannersString = BidRequest.builder()
                .imp(asList(
                        Imp.builder().banner(Banner.builder().build()).bidfloor(BigDecimal.valueOf(1.54)).build(),
                        Imp.builder().banner(Banner.builder().build()).bidfloor(BigDecimal.valueOf(1.33)).build()))
                .ext(jacksonMapper.fillExtension(ExtRequest.empty(), SomoaudienceReqExt.of("hb_pbs_1.0.0")))
                .build();
        final BidRequest videoRequest =
                expectedRequest(impBuilder -> impBuilder.video(Video.builder().build()), 1.97);
        final BidRequest nativeRequest =
                expectedRequest(impBuilder -> impBuilder.xNative(Native.builder().build()), 2.52);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri, HttpRequest::getPayload)
                .containsExactlyInAnyOrder(
                        tuple("http://somoaudience.com?s=placement2", expectedBannersString),
                        tuple("http://somoaudience.com?s=placement3", videoRequest),
                        tuple("http://somoaudience.com?s=placement4", nativeRequest));
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenMediaTypeIsAudio() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .audio(Audio.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null, ExtImpSomoaudience.of("placementId", null)))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput(
                        "SomoAudience only supports [banner, video, native] imps. Ignoring imp id : impId"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnHttpRequestWithErrorMessage() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(impBuilder -> impBuilder
                                .id("impId")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                        givenImp(impBuilder -> impBuilder
                                .id("impId2")
                                .ext(mapper.valueToTree(ExtPrebid.of(
                                        null, ExtImpSomoaudience.of("placementId", null)))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = somoaudienceBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("ignoring imp id=impId, error while decoding");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("http://somoaudience.com?s=placementId");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .containsExactly(
                        expectedRequest(impBuilder -> impBuilder.id("impId2").banner(Banner.builder().build()), null));
    }

    @Test
    public void makeBidsShouldReturnBidWithoutErrors() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(givenBidResponse(identity()));
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId")
                .banner(Banner.builder().build()).build()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldTakeBannerPrecedencyOverAllOtherMediaTypes() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(givenBidResponse(identity()));
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
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldTakeVideoPrecedencyOverNative() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(givenBidResponse(identity()));
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId")
                .video(Video.builder().build())
                .xNative(Native.builder().build()).build()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.video, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnBidsWithNativeTypeOnlyWhenAllOtherAreAbsent() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(givenBidResponse(identity()));
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("impId")
                .xNative(Native.builder().build()).build()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.xNative, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnBidsWithMediaBidTypeIfMediaTypeWasNotDefinedInImp() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(givenBidResponse(identity()));
        final BidRequest bidRequest =
                BidRequest.builder().imp(singletonList(Imp.builder().id("impId").build())).build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnBidWithMediaBidTypeIfCorrespondentImpWasNotFound() throws JsonProcessingException {
        // given
        final BidResponse response = givenBidResponse(identity());
        final BidRequest bidRequest =
                BidRequest.builder().imp(singletonList(Imp.builder().id("impId2").build())).build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(response));

        // when
        final Result<List<BidderBid>> result = somoaudienceBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, "EUR"));
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
                .extracting(Bid::getId)
                .containsExactly("bidId1", "bidId2");
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
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: ");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .user(User.builder().ext(ExtUser.builder().consent("consent").build()).build())
                .device(Device.builder().ua("User Agent").ip("ip").dnt(1).language("en").build())
                .regs(Regs.of(0, ExtRegs.of(1, null)))
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        null, ExtImpSomoaudience.of("placementId", BigDecimal.valueOf(1.39))))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).impid("impId").build()))
                        .build()))
                .build();
    }

    private static BidRequest expectedRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer, Double bidFloor) {
        return BidRequest.builder()
                .ext(jacksonMapper.fillExtension(ExtRequest.empty(), SomoaudienceReqExt.of("hb_pbs_1.0.0")))
                .imp(singletonList(impCustomizer.apply(Imp.builder()
                        .bidfloor(bidFloor != null ? BigDecimal.valueOf(bidFloor) : null))
                        .build())).build();
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
