package org.prebid.server.bidder.adkerneladn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
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
import org.prebid.server.proto.openrtb.ext.request.adkerneladn.ExtImpAdkernelAdn;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class AdkernelAdnBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.domain.com/rtbpub?account=";

    private AdkernelAdnBidder adkernelAdnBidder;

    @Before
    public void setUp() {
        adkernelAdnBidder = new AdkernelAdnBidder(ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdkernelAdnBidder("invalid_url"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpHasNoBannerOrVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid imp with id=123. Expected imp.banner or imp.video"));
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
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtPubIdIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), extBuilder -> extBuilder.pubId(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid pubId value. Ignoring imp id=123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfPubIdIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), extBuilder -> extBuilder.pubId(0));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid pubId value. Ignoring imp id=123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnErrorIfBannerFormatIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().format(null).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Expected at least one banner.format entry or explicit w/h"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(
                Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns("http://test.domain.com/rtbpub?account=50357", HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple("x-openrtb-version", "2.5"));
    }

    @Test
    public void makeHttpRequestShouldChangeDomainIfHostIsSpecified() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpAdkernelAdnBuilder -> extImpAdkernelAdnBuilder.host("different.domanin.com"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsOnly("http://different.domanin.com/rtbpub?account=50357");
    }

    @Test
    public void makeHttpRequestShouldRemovePortIfHostIsSpecified() {
        // given
        final String urlWithPort = "http://test:8080/rtbpub?account=";
        adkernelAdnBidder = new AdkernelAdnBidder(urlWithPort);

        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpAdkernelAdnBuilder -> extImpAdkernelAdnBuilder.host("different.domanin.com"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsOnly("http://different.domanin.com/rtbpub?account=50357");
    }

    @Test
    public void makeHttpRequestShouldAlwaysSetImpExtNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

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
                        .banner(Banner.builder().w(100).h(200).build())
                        .xNative(Native.builder().build())
                        .audio(Audio.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner, Imp::getVideo, Imp::getAudio, Imp::getXNative)
                .containsOnly(tuple(Banner.builder().w(100).h(200).build(), null, null, null));
    }

    @Test
    public void makeHttpRequestShouldSetBannerWidthHeightFromFirstFormat() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(250).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .flatExtracting(Imp::getBanner)
                .containsOnly(Banner.builder()
                        .format(emptyList())
                        .w(300).h(250).build());
    }

    @Test
    public void makeHttpRequestShouldSetExtractFirstFormatToBannerWidthHeight() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(asList(
                                        Format.builder().w(300).h(250).build(),
                                        Format.builder().w(400).h(200).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .flatExtracting(Imp::getBanner)
                .containsOnly(Banner.builder()
                        .format(singletonList(Format.builder().w(400).h(200).build()))
                        .w(300).h(250).build());
    }

    @Test
    public void makeHttpRequestShouldSetAudioAndNativeNullAndKeepBannerNullWhenVideoIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .xNative(Native.builder().build())
                        .audio(Audio.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

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
                builder -> builder.site(Site.builder()
                        .domain("some domain")
                        .publisher(Publisher.builder().build())
                        .build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .containsOnly(Site.builder().publisher(null).domain("").build());
    }

    @Test
    public void makeHttpRequestShouldModifyApp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.app(App.builder()
                        .publisher(Publisher.builder().build())
                        .build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adkernelAdnBidder.makeHttpRequests(bidRequest);

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
        final Result<List<BidderBid>> result = adkernelAdnBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = adkernelAdnBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = adkernelAdnBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = adkernelAdnBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = adkernelAdnBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = adkernelAdnBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(adkernelAdnBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpAdkernelAdn.ExtImpAdkernelAdnBuilder, ExtImpAdkernelAdn.ExtImpAdkernelAdnBuilder> extCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpAdkernelAdn.ExtImpAdkernelAdnBuilder, ExtImpAdkernelAdn.ExtImpAdkernelAdnBuilder> extCustomizer) {

        return givenBidRequest(identity(), impCustomizer, extCustomizer);
    }

    private static Imp givenImp(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpAdkernelAdn.ExtImpAdkernelAdnBuilder, ExtImpAdkernelAdn.ExtImpAdkernelAdnBuilder> extCustomizer) {

        return impCustomizer.apply(Imp.builder()
                .id("123")
                .video(Video.builder().build())
                .ext(mapper.valueToTree(
                        ExtPrebid.of(null, extCustomizer.apply(ExtImpAdkernelAdn.builder().pubId(50357)).build()))))
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