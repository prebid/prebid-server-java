package org.prebid.server.bidder.resetdigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalImp;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalImpMediaType;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalImpMediaTypes;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalImpZone;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalRequest;
import org.prebid.server.bidder.resetdigital.request.ResetDigitalSite;
import org.prebid.server.bidder.resetdigital.response.ResetDigitalBid;
import org.prebid.server.bidder.resetdigital.response.ResetDigitalResponse;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.resetdigital.ExtImpResetDigital;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.ACCEPT_LANGUAGE_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.REFERER_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.prebid.server.util.HttpUtil.X_FORWARDED_FOR_HEADER;
import static org.prebid.server.util.HttpUtil.X_REAL_IP_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class ResetDigitalBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private ResetDigitalBidder target;

    @BeforeEach
    public void setUp() {
        target = new ResetDigitalBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ResetDigitalBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(bidderError.getMessage()).startsWith("Cannot deserialize value");
        });
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp1"),
                imp -> imp.id("givenImp2"),
                imp -> imp.id("givenImp3"));

        //when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(3)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(ResetDigitalRequest::getImps)
                .extracting(ResetDigitalImp::getImpId)
                .containsExactlyInAnyOrder("givenImp1", "givenImp2", "givenImp3");

        assertThat(result.getValue()).hasSize(3)
                .extracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder(Set.of("givenImp1"), Set.of("givenImp2"), Set.of("givenImp3"));
    }

    @Test
    public void makeHttpRequestsShouldHaveCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp"));

        //when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactlyInAnyOrder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .device(Device.builder().ip("ip").ua("ua").language("lang").build())
                .site(Site.builder().page("page").build())
                .build();

        // when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                    assertThat(headers.get(USER_AGENT_HEADER)).isEqualTo("ua");
                    assertThat(headers.get(ACCEPT_LANGUAGE_HEADER)).isEqualTo("lang");
                    assertThat(headers.get(X_FORWARDED_FOR_HEADER)).isEqualTo("ip");
                    assertThat(headers.get(X_REAL_IP_HEADER)).isEqualTo("ip");
                    assertThat(headers.get(REFERER_HEADER)).isEqualTo("page");
                });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWhenDeviceAndSiteAreNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                    assertThat(headers.get(USER_AGENT_HEADER)).isNull();
                    assertThat(headers.get(ACCEPT_LANGUAGE_HEADER)).isNull();
                    assertThat(headers.get(X_FORWARDED_FOR_HEADER)).isNull();
                    assertThat(headers.get(X_REAL_IP_HEADER)).isNull();
                    assertThat(headers.get(REFERER_HEADER)).isNull();
                });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnBannerRequestFromBannerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .id("impId")
                .banner(Banner.builder().w(1).h(2).build()))
                .toBuilder()
                .id("requestId")
                .build();

        //when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(ResetDigitalRequest::getImps)
                .containsExactlyInAnyOrder(ResetDigitalImp.builder()
                        .impId("impId")
                        .bidId("requestId")
                        .zoneId(ResetDigitalImpZone.of("placementId"))
                        .mediaTypes(ResetDigitalImpMediaTypes.banner(ResetDigitalImpMediaType.builder()
                                .sizes(List.of(List.of(1, 2)))
                                .build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnVideoRequestFromVideoImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .id("impId")
                .banner(null)
                .video(Video.builder().w(1).h(2).mimes(List.of("mime1", "mime2")).build()))
                .toBuilder()
                .id("requestId")
                .build();

        //when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(ResetDigitalRequest::getImps)
                .containsExactlyInAnyOrder(ResetDigitalImp.builder()
                        .impId("impId")
                        .bidId("requestId")
                        .zoneId(ResetDigitalImpZone.of("placementId"))
                        .mediaTypes(ResetDigitalImpMediaTypes.video(ResetDigitalImpMediaType.builder()
                                .sizes(List.of(List.of(1, 2)))
                                .mimes(List.of("mime1", "mime2"))
                                .build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnAudioRequestFromAudioImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .id("impId")
                .banner(null)
                .audio(Audio.builder().mimes(List.of("mime1", "mime2")).build()))
                .toBuilder()
                .id("requestId")
                .build();

        //when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(ResetDigitalRequest::getImps)
                .containsExactlyInAnyOrder(ResetDigitalImp.builder()
                        .impId("impId")
                        .bidId("requestId")
                        .zoneId(ResetDigitalImpZone.of("placementId"))
                        .mediaTypes(ResetDigitalImpMediaTypes.audio(ResetDigitalImpMediaType.builder()
                                .mimes(List.of("mime1", "mime2"))
                                .build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnFailWhenImpIsNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .id("impId")
                .banner(null)
                .xNative(Native.builder().build()))
                .toBuilder()
                .id("requestId")
                .build();

        //when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(bidderError.getMessage()).isEqualTo("Banner, video or audio must be present in the imp impId");
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnCorrectSite() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .site(Site.builder().domain("domain").page("page").build())
                .build();

        //when
        final Result<List<HttpRequest<ResetDigitalRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(ResetDigitalRequest::getSite)
                .containsExactlyInAnyOrder(ResetDigitalSite.builder()
                        .referrer("page")
                        .domain("domain")
                        .build());
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<ResetDigitalRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid':");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyBidsWhenResponseDoesNotHaveBids() throws JsonProcessingException {
        // given
        final BidderCall<ResetDigitalRequest> httpCall = givenHttpCall(givenBidResponse());

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors())
                .containsOnly(BidderError.badServerResponse("expected exactly one bid in the response, but got 0"));
    }

    @Test
    public void makeBidsShouldReturnEmptyBidsWhenResponseHasMoreThanOneBid() throws JsonProcessingException {
        // given
        final BidderCall<ResetDigitalRequest> httpCall = givenHttpCall(givenBidResponse(identity(), identity()));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors())
                .containsOnly(BidderError.badServerResponse("expected exactly one bid in the response, but got 2"));
    }

    @Test
    public void makeBidsShouldReturnEmptyBidsWhenResponseDoesNotHaveBidThatMatchesAnyImp()
            throws JsonProcessingException {

        // given
        final BidderCall<ResetDigitalRequest> httpCall = givenHttpCall(givenBidResponse(bid -> bid.impId("impId1")));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, givenBidRequest(imp -> imp.id("impId2")));

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors())
                .containsOnly(BidderError.badServerResponse("no matching impression found for ImpID impId1"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final BidderCall<ResetDigitalRequest> httpCall = givenHttpCall(givenBidResponse(bid -> bid
                .cpm(BigDecimal.TEN)
                .cid("cid")
                .html("html")
                .crid("crid")
                .seat("seat")
                .impId("impId")
                .bidId("bidId")
                .w("1")
                .h("2")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(
                httpCall,
                givenBidRequest(imp -> imp.id("impId").banner(Banner.builder().build())));

        // then
        assertThat(result.getErrors()).isEmpty();
        final Bid expectedBid = Bid.builder()
                .adm("html")
                .price(BigDecimal.TEN)
                .cid("cid")
                .w(1)
                .h(2)
                .impid("impId")
                .id("bidId")
                .crid("crid")
                .build();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, banner, "seat", "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final BidderCall<ResetDigitalRequest> httpCall = givenHttpCall(givenBidResponse(bid -> bid
                .cpm(BigDecimal.TEN)
                .cid("cid")
                .html("html")
                .crid("crid")
                .seat("seat")
                .impId("impId")
                .bidId("bidId")
                .w("1")
                .h("2")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(
                httpCall,
                givenBidRequest(imp -> imp.id("impId").video(Video.builder().build())));

        // then
        assertThat(result.getErrors()).isEmpty();
        final Bid expectedBid = Bid.builder()
                .adm("html")
                .price(BigDecimal.TEN)
                .cid("cid")
                .w(1)
                .h(2)
                .impid("impId")
                .id("bidId")
                .crid("crid")
                .build();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, video, "seat", "USD"));
    }

    @Test
    public void makeBidsShouldReturnAudioBidSuccessfully() throws JsonProcessingException {
        // given
        final BidderCall<ResetDigitalRequest> httpCall = givenHttpCall(givenBidResponse(bid -> bid
                .cpm(BigDecimal.TEN)
                .cid("cid")
                .html("html")
                .crid("crid")
                .seat("seat")
                .impId("impId")
                .bidId("bidId")
                .w("1")
                .h("2")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(
                httpCall,
                givenBidRequest(imp -> imp.id("impId").audio(Audio.builder().build())));

        // then
        assertThat(result.getErrors()).isEmpty();
        final Bid expectedBid = Bid.builder()
                .adm("html")
                .price(BigDecimal.TEN)
                .cid("cid")
                .w(1)
                .h(2)
                .impid("impId")
                .id("bidId")
                .crid("crid")
                .build();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, audio, "seat", "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Stream.of(impCustomizers).map(ResetDigitalBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().w(23).h(25).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null,
                                ExtImpResetDigital.of("placementId")))))
                .build();
    }

    private static BidderCall<ResetDigitalRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<ResetDigitalRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private String givenBidResponse(UnaryOperator<ResetDigitalBid.ResetDigitalBidBuilder>... bidCustomizers)
            throws JsonProcessingException {

        return mapper.writeValueAsString(ResetDigitalResponse.of(
                Stream.of(bidCustomizers)
                        .map(bidCustomizer -> bidCustomizer.apply(ResetDigitalBid.builder()).build())
                        .toList()));
    }
}
