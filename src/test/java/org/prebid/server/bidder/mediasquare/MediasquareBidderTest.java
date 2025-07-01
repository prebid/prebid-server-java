package org.prebid.server.bidder.mediasquare;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.mediasquare.request.MediasquareBanner;
import org.prebid.server.bidder.mediasquare.request.MediasquareCode;
import org.prebid.server.bidder.mediasquare.request.MediasquareFloor;
import org.prebid.server.bidder.mediasquare.request.MediasquareGdpr;
import org.prebid.server.bidder.mediasquare.request.MediasquareMediaTypes;
import org.prebid.server.bidder.mediasquare.request.MediasquareRequest;
import org.prebid.server.bidder.mediasquare.request.MediasquareSupport;
import org.prebid.server.bidder.mediasquare.response.MediasquareBid;
import org.prebid.server.bidder.mediasquare.response.MediasquareResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.mediasquare.ExtImpMediasquare;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
public class MediasquareBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final MediasquareBidder target = new MediasquareBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MediasquareBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<MediasquareRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("can not parse imp.ext");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldHaveImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp1"), imp -> imp.id("givenImp2"));

        //when
        final Result<List<HttpRequest<MediasquareRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getImpIds)
                .containsOnly(Set.of("givenImp1", "givenImp2"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<MediasquareRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<MediasquareRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectRequestPayload() {
        // given
        final Device givenDevice = Device.builder().build();
        final App givenApp = App.builder().build();
        final ExtRegsDsa givenDsa = ExtRegsDsa.of(1, 2, 3, Collections.emptyList());

        final BidRequest bidRequest = givenBidRequest(
                imp -> imp
                        .id("imp_id_1")
                        .tagid("tag_id_1")
                        .bidfloor(BigDecimal.ONE)
                        .bidfloorcur("USD")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(250).build()))
                                .build())
                        .ext(givenImpExt("owner1", "code1")),
                imp -> imp
                        .id("imp_id_2")
                        .tagid("tag_id_2")
                        .bidfloor(BigDecimal.TEN)
                        .bidfloorcur("EUR")
                        .banner(null)
                        .video(Video.builder().w(640).h(480).build())
                        .ext(givenImpExt("owner2", "code2")),
                imp -> imp
                        .id("imp_id_3")
                        .tagid("tag_id_3")
                        .bidfloor(BigDecimal.valueOf(0.5))
                        .bidfloorcur("USD")
                        .banner(null)
                        .xNative(Native.builder().request("native_request_str").build())
                        .ext(givenImpExt("owner3", "code3")))
                .toBuilder()
                .id("request_id")
                .test(1)
                .user(User.builder().consent("consent_str").build())
                .regs(Regs.builder().gdpr(1).ext(ExtRegs.of(null, null, null, givenDsa)).build())
                .device(givenDevice)
                .app(givenApp)
                .build();

        // when
        final Result<List<HttpRequest<MediasquareRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final MediasquareFloor bannerFloor = MediasquareFloor.of(BigDecimal.ONE, "USD");
        final MediasquareFloor videoFloor = MediasquareFloor.of(BigDecimal.TEN, "EUR");
        final MediasquareFloor nativeFloor = MediasquareFloor.of(BigDecimal.valueOf(0.5), "USD");

        final MediasquareCode expectedCode1 = MediasquareCode.builder()
                .adUnit("tag_id_1")
                .auctionId("request_id")
                .bidId("imp_id_1")
                .owner("owner1")
                .code("code1")
                .mediaTypes(MediasquareMediaTypes.builder()
                        .banner(MediasquareBanner.of(List.of(List.of(300, 250))))
                        .build())
                .floor(Map.of("300x250", bannerFloor))
                .build();
        final MediasquareCode expectedCode2 = MediasquareCode.builder()
                .adUnit("tag_id_2")
                .auctionId("request_id")
                .bidId("imp_id_2")
                .owner("owner2")
                .code("code2")
                .mediaTypes(MediasquareMediaTypes.builder()
                        .video(Video.builder().w(640).h(480).build())
                        .build())
                .floor(Map.of("640x480", videoFloor, "*", videoFloor))
                .build();
        final MediasquareCode expectedCode3 = MediasquareCode.builder()
                .adUnit("tag_id_3")
                .auctionId("request_id")
                .bidId("imp_id_3")
                .owner("owner3")
                .code("code3")
                .mediaTypes(MediasquareMediaTypes.builder()
                        .nativeRequest("native_request_str")
                        .build())
                .floor(Map.of("*", nativeFloor))
                .build();

        final MediasquareRequest expectedRequest = MediasquareRequest.builder()
                .codes(List.of(expectedCode1, expectedCode2, expectedCode3))
                .dsa(mapper.valueToTree(givenDsa))
                .gdpr(MediasquareGdpr.of(true, "consent_str"))
                .type("pbs")
                .support(MediasquareSupport.of(
                        mapper.valueToTree(givenDevice),
                        mapper.valueToTree(givenApp)))
                .test(true)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(expectedRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<MediasquareRequest> httpCall = givenHttpCall("invalid_json");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith(
                            "Failed to decode response: Failed to decode: Unrecognized token 'invalid_json'");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final MediasquareBid mediasquareBid = givenMediasquareBid(1);
        final BidderCall<MediasquareRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(MediasquareResponse.of(List.of(mediasquareBid))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final ObjectNode expectedExt = mapper.createObjectNode()
                .set("prebid", mapper.valueToTree(ExtBidPrebid.builder().meta(ExtBidPrebidMeta.builder()
                        .mediaType("banner")
                        .advertiserDomains(List.of("adomain.com"))
                        .build()).build()));
        expectedExt.set("dsa", mapper.createObjectNode().put("key", "value"));

        final Bid expectedBid = Bid.builder()
                .id("bidId")
                .impid("impId")
                .price(BigDecimal.valueOf(1.23))
                .adm("ad-markup")
                .adomain(List.of("adomain.com"))
                .w(300)
                .h(250)
                .crid("crid")
                .mtype(1)
                .burl("burl")
                .ext(expectedExt)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(expectedBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final MediasquareBid mediasquareBid = givenMediasquareBid(2);
        final BidderCall<MediasquareRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(MediasquareResponse.of(List.of(mediasquareBid))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final ObjectNode expectedExt = mapper.createObjectNode()
                .set("prebid", mapper.valueToTree(ExtBidPrebid.builder().meta(ExtBidPrebidMeta.builder()
                        .mediaType("video")
                        .advertiserDomains(List.of("adomain.com"))
                        .build()).build()));
        expectedExt.set("dsa", mapper.createObjectNode().put("key", "value"));

        final Bid expectedBid = Bid.builder()
                .id("bidId")
                .impid("impId")
                .price(BigDecimal.valueOf(1.23))
                .adm("ad-markup")
                .adomain(List.of("adomain.com"))
                .w(300)
                .h(250)
                .crid("crid")
                .mtype(2)
                .burl("burl")
                .ext(expectedExt)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(expectedBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final MediasquareBid mediasquareBid = givenMediasquareBid(4);
        final BidderCall<MediasquareRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(MediasquareResponse.of(List.of(mediasquareBid))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final ObjectNode expectedExt = mapper.createObjectNode()
                .set("prebid", mapper.valueToTree(ExtBidPrebid.builder().meta(ExtBidPrebidMeta.builder()
                        .mediaType("native")
                        .advertiserDomains(List.of("adomain.com"))
                        .build()).build()));
        expectedExt.set("dsa", mapper.createObjectNode().put("key", "value"));

        final Bid expectedBid = Bid.builder()
                .id("bidId")
                .impid("impId")
                .price(BigDecimal.valueOf(1.23))
                .adm("ad-markup")
                .adomain(List.of("adomain.com"))
                .w(300)
                .h(250)
                .crid("crid")
                .mtype(4)
                .burl("burl")
                .ext(expectedExt)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(expectedBid, BidType.xNative, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(MediasquareBidderTest::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("imp_id")
                        .tagid("tag_id")
                        .bidfloor(BigDecimal.ONE)
                        .bidfloorcur("USD")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(250).build()))
                                .build())
                        .ext(givenImpExt("owner", "code")))
                .build();
    }

    private static ObjectNode givenImpExt(String owner, String code) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpMediasquare.of(owner, code)));
    }

    private static MediasquareBid givenMediasquareBid(Integer mtype) {
        final MediasquareBid.MediasquareBidBuilder builder = MediasquareBid.builder()
                .id("bidId")
                .bidId("impId")
                .cpm(BigDecimal.valueOf(1.23))
                .ad("ad-markup")
                .adomain(List.of("adomain.com"))
                .width(300)
                .height(250)
                .creativeId("crid")
                .burl("burl")
                .dsa(mapper.createObjectNode().put("key", "value"))
                .currency("USD");

        if (mtype == 2) {
            builder.video(mapper.createObjectNode());
        } else if (mtype == 4) {
            builder.nativeResponse(mapper.createObjectNode());
        }
        return builder.build();
    }

    private static BidderCall<MediasquareRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<MediasquareRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
