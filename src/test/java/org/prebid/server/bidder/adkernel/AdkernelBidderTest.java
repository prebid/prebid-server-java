package org.prebid.server.bidder.adkernel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adkernel.ExtImpAdkernel;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class AdkernelBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://%s/hb?zone=%s";

    private AdkernelBidder adkernelBidder;

    @Before
    public void setUp() {
        adkernelBidder = new AdkernelBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AdkernelBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpHasNoBannerOrVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isNull();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid imp id=123. Expected imp.banner or imp.video"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtZoneIdIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), extBuilder -> extBuilder.zoneId(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid zoneId value: null. Ignoring imp id=123"));
        assertThat(result.getValue()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtZoneIdIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), extBuilder -> extBuilder.zoneId(0));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid zoneId value: 0. Ignoring imp id=123"));
        assertThat(result.getValue()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtHostIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), extBuilder -> extBuilder.host(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Host is empty. Ignoring imp id=123"));
        assertThat(result.getValue()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtHostIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), extBuilder -> extBuilder.host(" "));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Host is empty. Ignoring imp id=123"));
        assertThat(result.getValue()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedMethodUrlAndHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns("http://test_host/hb?zone=3426", HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple("x-openrtb-version", "2.5"));
    }

    @Test
    public void makeHttpRequestShouldAlwaysSetImpExtNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.site(Site.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt).hasSize(1)
                .containsNull();
    }

    @Test
    public void makeHttpRequestShouldSetAudioVideoAndNativeNullAndKeepBannerWhenBannerIsPresent() {
        // give
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().build())
                        .xNative(Native.builder().build())
                        .audio(Audio.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner, Imp::getVideo, Imp::getAudio, Imp::getXNative)
                .containsOnly(tuple(Banner.builder().build(), null, null, null));
    }

    @Test
    public void makeHttpRequestShouldSetAudioAndNativeNullAndKeepBannerNullWhenVideoIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .xNative(Native.builder().build())
                        .audio(Audio.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner, Imp::getVideo, Imp::getAudio, Imp::getXNative)
                .containsOnly(tuple(null, Video.builder().build(), null, null));
    }

    @Test
    public void makeHttpRequestShouldModifySite() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().publisher(Publisher.builder().build()).build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .containsOnly(Site.builder().publisher(null).build());
    }

    @Test
    public void makeHttpRequestShouldModifyApp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(null)
                        .app(App.builder().publisher(Publisher.builder().build()).build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .containsOnly(App.builder().publisher(null).build());
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = adkernelBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = adkernelBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = adkernelBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenSeatBidsCountIsNotOne() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = adkernelBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("Invalid SeatBids count: 0"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("123")
                        .video(Video.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adkernelBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfRequestImpHasBanner() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder.id("123")
                        .video(Video.builder().build())
                        .banner(Banner.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adkernelBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(adkernelBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpAdkernel.ExtImpAdkernelBuilder, ExtImpAdkernel.ExtImpAdkernelBuilder> extCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .site(Site.builder().build())
                .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpAdkernel.ExtImpAdkernelBuilder, ExtImpAdkernel.ExtImpAdkernelBuilder> extCustomizer) {
        return givenBidRequest(identity(), impCustomizer, extCustomizer);
    }

    private static Imp givenImp(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpAdkernel.ExtImpAdkernelBuilder, ExtImpAdkernel.ExtImpAdkernelBuilder> extCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        extCustomizer.apply(ExtImpAdkernel.builder().zoneId(3426).host("test_host")).build()))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
