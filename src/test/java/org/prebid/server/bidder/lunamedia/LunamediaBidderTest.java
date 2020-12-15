package org.prebid.server.bidder.lunamedia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
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
import org.prebid.server.proto.openrtb.ext.request.lunamedia.ExtImpLunamedia;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class LunamediaBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test/get?pubid=";

    private LunamediaBidder lunamediaBidder;

    @Before
    public void setUp() {
        lunamediaBidder = new LunamediaBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LunamediaBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenExtPubIdIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), ExtImpLunamedia.of(null, null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("No pubid value provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenExtPubIdIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), ExtImpLunamedia.of(" ", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("No pubid value provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnErrorWhenBannerFormatIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().format(null).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Expected at least one banner.format entry or explicit w/h"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnAllErrorsWithRequest() {
        // given
        final Imp impWithoutFormatFirst =
                givenImp(impBuilder -> impBuilder.banner(Banner.builder().format(null).build()));
        final Imp impWithoutFormatSecond =
                givenImp(impBuilder -> impBuilder.banner(Banner.builder().format(null).build()));
        final Imp impWithoutType = givenImp(identity());
        final Imp impWithoutPubIdFirst = givenImp(identity(), ExtImpLunamedia.of(" ", null));
        final Imp impWithoutPubIdSecond = givenImp(identity(), ExtImpLunamedia.of(" ", null));
        final Imp normalImp = givenImp(impBuilder -> impBuilder.video(Video.builder().build()));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(impWithoutFormatFirst, impWithoutPubIdFirst, impWithoutFormatSecond, impWithoutPubIdSecond,
                        normalImp, impWithoutType))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(5)
                .containsOnly(BidderError.badInput("No pubid value provided"),
                        BidderError.badInput("No pubid value provided"),
                        BidderError.badInput("Expected at least one banner.format entry or explicit w/h"),
                        BidderError.badInput("Expected at least one banner.format entry or explicit w/h"),
                        BidderError.badInput("Unsupported impression has been received"));
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestShouldReturnErrorWhenNoSupportedBidderTypeProvided() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.xNative(Native.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Unsupported impression has been received"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(
                Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns("http://test/get?pubid=pubid", HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestShouldSetImpExtNullAndXnativeNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .xNative(Native.builder().build())
                        .video(Video.builder().build()),
                ExtImpLunamedia.of("pubid", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        final Imp expectedImp = Imp.builder()
                .video(Video.builder().build())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .containsOnly(expectedImp);
    }

    @Test
    public void makeHttpRequestShouldSetTagidFromExtPlacement() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .tagid("was")
                        .video(Video.builder().build()),
                ExtImpLunamedia.of("pubid", "setPlacement"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        final Imp expectedImp = Imp.builder()
                .video(Video.builder().build())
                .tagid("setPlacement")
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .containsOnly(expectedImp);
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
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

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
    public void makeHttpRequestShouldModifySite() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder()
                        .domain("some domain")
                        .publisher(Publisher.builder().build())
                        .build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                ExtImpLunamedia.of("pubid", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

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
                impBuilder -> impBuilder.video(Video.builder().build()),
                ExtImpLunamedia.of("pubid", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = lunamediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .containsOnly(App.builder().publisher(null).build());
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = lunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = lunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = lunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenTypeNotPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = lunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenVideoPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("123")
                        .video(Video.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = lunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            ExtImpLunamedia extImpLunamedia) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extImpLunamedia))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, ExtImpLunamedia.of("pubid", "placment"));
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                              ExtImpLunamedia extImpLunamedia) {
        return givenBidRequest(identity(), impCustomizer, extImpLunamedia);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                ExtImpLunamedia extImpLunamedia) {
        return impCustomizer.apply(Imp.builder()
                .ext(mapper.valueToTree(
                        ExtPrebid.of(null, extImpLunamedia))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpLunamedia.of("pubid", "placment")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
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
