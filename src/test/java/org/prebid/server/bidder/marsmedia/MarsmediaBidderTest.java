package org.prebid.server.bidder.marsmedia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.marsmedia.ExtImpMarsmedia;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class MarsmediaBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/test";

    private MarsmediaBidder marsmediaBidder;

    @Before
    public void setUp() {
        marsmediaBidder = new MarsmediaBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MarsmediaBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("ext.bidder not provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtZoneIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpMarsmedia.of(" ")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("ZoneId is empty"));
    }

    @Test
    public void makeHttpRequestsShouldResolveZoneFromJsonZoneIdField() {
        // given
        final ExtPrebid<?, ObjectNode> impExt = ExtPrebid.of(
                null,
                mapper.createObjectNode().set("zoneId", TextNode.valueOf("zoneId")));

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mapper.valueToTree(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .allSatisfy(uri -> {
                    final String regex = "[a-zA-Z0-9\\:\\/\\._\\-\\?&\\=]+zone=(?<zone>[a-zA-Z0-9]+)";
                    final Matcher matcher = Pattern.compile(regex).matcher(uri);
                    assertThat(matcher.find()).isTrue();
                    assertThat(matcher.group("zone")).isEqualTo("zoneId");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpBannerHasNoSizeOrFormats() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("No valid banner format in the bid request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfThereAreNoValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No valid impression in the bid request"));
    }

    @Test
    public void makeHttpRequestsShouldAddAtAttributeToOutgoingRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getAt)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldAddOnlyBannerAndVideoImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(impBuilder -> impBuilder.id("123")),
                        givenImp(impBuilder -> impBuilder.id("456").banner(null).video(Video.builder().build())),
                        givenImp(impBuilder -> impBuilder.id("789").banner(null).xNative(Native.builder().build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("123", "456");
    }

    @Test
    public void makeHttpRequestsShouldReplaceBannerWidthAndHeightWithValuesFromFirstFormat() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(640).h(480).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsExactly(tuple(640, 480));
    }

    @Test
    public void makeHttpRequestsShouldReplaceBannerWidthAndHeightWithZeroIfFormatValuesNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(null).h(null).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsExactly(tuple(0, 0));
    }

    @Test
    public void makeHttpRequestsShouldAlwaysSetRequestAtToOne() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getAt)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedRequestUriAndBasicHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/test&zone=zoneId");
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestsShouldSetAdditionalHeadersIfRequestDeviceIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder
                        .device(Device.builder()
                                .ua("userAgent")
                                .ip("127.0.0.1")
                                .dnt(1)
                                .language("en")
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = marsmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple("User-Agent", "userAgent"),
                        tuple("X-Forwarded-For", "127.0.0.1"),
                        tuple("Accept-Language", "en"),
                        tuple("DNT", "1"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = marsmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = marsmediaBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = marsmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("125"))));

        // when
        final Result<List<BidderBid>> result = marsmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("125").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfFirstSeatIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder().build(),
                mapper.writeValueAsString(BidResponse.builder().seatbid(singletonList(null)).build()));

        // when
        final Result<List<BidderBid>> result = marsmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = marsmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impModifier,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestModifier) {

        return requestModifier.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impModifier))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impModifier) {
        return givenBidRequest(impModifier, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impModifier) {
        return impModifier.apply(Imp.builder()
                        .banner(Banner.builder().h(150).w(300).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpMarsmedia.of("zoneId")))))
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
