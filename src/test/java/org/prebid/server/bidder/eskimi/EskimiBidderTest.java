package org.prebid.server.bidder.eskimi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.eskimi.ExtImpEskimi;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

@ExtendWith(MockitoExtension.class)
public class EskimiBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";
    private EskimiBidder target;

    @BeforeEach
    public void setUp() {
        target = new EskimiBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EskimiBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenFirstImpExtIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .hasSize(1)
                .allSatisfy(error -> assertThat(error).startsWith("invalid imp.ext for imp 123"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSiteAndAppAreMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity())).toBuilder()
                .site(null)
                .app(null)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("request must contain either site or app");
    }

    @Test
    public void makeHttpRequestsShouldAddErrorsOnInvalidImpsAndProcessValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(identity()),
                givenImp(imp -> imp.id("456").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))))
        );

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .hasSize(1)
                .allSatisfy(error -> assertThat(error).startsWith("invalid imp.ext for imp 456"));
    }

    @Test
    public void makeHttpRequestsShouldModifyImpSettingBattrForBannerAndVideoWhenEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                        .ext(givenImpExt(givenExtImpBattr(List.of(1, 2))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner, Imp::getVideo)
                .containsExactly(tuple(
                        Banner.builder().battr(List.of(1, 2)).build(),
                        Video.builder().battr(List.of(1, 2)).build()));
    }

    @Test
    public void makeHttpRequestsShouldNotOverrideBattrWhenAlreadyPresentInBannerOrVideo() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .banner(Banner.builder().battr(List.of(1, 2)).build())
                        .video(Video.builder().battr(List.of(3, 4)).build())
                        .ext(givenImpExt(givenExtImpBattr(List.of(5, 6))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner, Imp::getVideo)
                .containsExactly(tuple(
                        Banner.builder().battr(List.of(1, 2)).build(),
                        Video.builder().battr(List.of(3, 4)).build()));
    }

    @Test
    public void makeHttpRequestsShouldSetSecureToOneWhenNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.secure(null)),
                givenImp(imp -> imp.secure(0)),
                givenImp(imp -> imp.secure(1)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getSecure)
                .containsExactly(1, 0, 1);
    }

    @Test
    public void makeHttpRequestsShouldUseImpBidfloorAndCurWhenValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .bidfloor(BigDecimal.TEN)
                        .bidfloorcur("USD")
                        .ext(givenImpExt(givenExtImpBidFloor(BigDecimal.ONE, "EUR")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldFallbackToExtBidfloorAndCurWhenImpInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .bidfloor(BigDecimal.ZERO)
                        .bidfloorcur(null)
                        .ext(givenImpExt(givenExtImpBidFloor(BigDecimal.ONE, "EUR")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.ONE, "EUR"));
    }

    @Test
    public void makeHttpRequestsShouldFallbackToExtBidfloorButKeepImpCurWhenExtCurIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .bidfloor(null)
                        .bidfloorcur("USD")
                        .ext(givenImpExt(givenExtImpBidFloor(BigDecimal.ONE, null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.ONE, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldApplyRequestParamsFromFirstImpExtWhenMissingInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(givenImpExt(
                        givenExtImpBlocked(List.of("cat1"), List.of("adv1"), List.of("app1"))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getBcat, BidRequest::getBadv, BidRequest::getBapp)
                .containsExactly(tuple(List.of("cat1"), List.of("adv1"), List.of("app1")));
    }

    @Test
    public void makeHttpRequestsShouldNotOverrideRequestParamsWhenPresentInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(givenImpExt(
                        givenExtImpBlocked(List.of("ext_cat1"), List.of("ext_adv1"), List.of("ext_app1"))))))
                .toBuilder()
                .bcat(List.of("req_cat1"))
                .badv(List.of("req_adv1"))
                .bapp(List.of("req_app1"))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getBcat, BidRequest::getBadv, BidRequest::getBapp)
                .containsExactly(tuple(List.of("req_cat1"), List.of("req_adv1"), List.of("req_app1")));
    }

    @Test
    public void makeHttpRequestsShouldSetPlacementIdInSiteExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(givenImpExt(givenExtImpEskimi(1)))))
                .toBuilder()
                .site(Site.builder().build())
                .app(null)
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getExt)
                .extracting(ext -> ext.getProperty("placementId"))
                .containsExactly(IntNode.valueOf(1));
    }

    @Test
    public void makeHttpRequestsShouldSetPlacementIdInAppExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(givenImpExt(givenExtImpEskimi(1)))))
                .toBuilder()
                .site(null)
                .app(App.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getExt)
                .extracting(ext -> ext.getProperty("placementId"))
                .containsExactly(IntNode.valueOf(1));
    }

    @Test
    public void makeHttpRequestsShouldPreserveOriginalSiteExtAndSetPlacementId() {
        // given
        final ExtSite givenExtSite = ExtSite.of(null, mapper.createObjectNode().put("ANY", "ANY"));

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(givenImpExt(givenExtImpEskimi(1)))))
                .toBuilder()
                .site(Site.builder().ext(givenExtSite).build())
                .app(null)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ExtSite expectedExtSite = ExtSite.of(null, mapper.createObjectNode().put("ANY", "ANY"));
        expectedExtSite.addProperty("placementId", IntNode.valueOf(1));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getExt)
                .containsExactly(expectedExtSite);
    }

    @Test
    public void makeHttpRequestsShouldPreserveOriginalAppExtAndSetPlacementId() {
        // given
        final ExtApp givenExtApp = ExtApp.of(null, mapper.createObjectNode().put("ANY", "ANY"));
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(givenImpExt(givenExtImpEskimi(1)))))
                .toBuilder()
                .site(null)
                .app(App.builder().ext(givenExtApp).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ExtApp expectedExtApp = ExtApp.of(null, mapper.createObjectNode().put("ANY", "ANY"));
        expectedExtApp.addProperty("placementId", IntNode.valueOf(1));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getExt)
                .containsExactly(expectedExtApp);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(givenImp(identity())), "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .asString()
                .startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseHasNoSeatBids() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(Collections.emptyList())
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIsBanner() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenMtypeIsVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsUnsupported() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(3))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .isEqualTo("unsupported bid.mtype 3 for impression 123 (banner and video only)");
    }

    @Test
    public void makeBidsShouldResolveBannerBidTypeFromImpWhenMtypeIsMissing() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(Banner.builder().build()).video(null)));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.banner);
    }

    @Test
    public void makeBidsShouldResolveVideoBidTypeFromImpWhenMtypeIsMissing() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(null).video(Video.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .singleElement()
                .extracting(BidderBid::getType)
                .isEqualTo(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsMissingAndImpHasMultipleFormats() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(Banner.builder().build()).video(Video.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .isEqualTo("bid for multi-format imp 123 requires bid.mtype to disambiguate");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeMissingAndImpHasNoSupportedFormats() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .banner(null)
                        .video(null)
                        .audio(Audio.builder().build())
                        .xNative(Native.builder().build())));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .isEqualTo("unsupported media type for impression 123 (banner and video only)");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsMissingAndImpIdNotFound() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("404").mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(BidderError::getMessage)
                .isEqualTo("unable to resolve media type for impression 404");
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder()
                .cur(List.of("USD"))
                .site(Site.builder().build())
                .imp(Arrays.stream(imps).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(givenImpExt(givenExtImpEskimi(1))))
                .build();
    }

    private static ObjectNode givenImpExt(ExtImpEskimi extImpEskimi) {
        return mapper.valueToTree(ExtPrebid.of(null, extImpEskimi));
    }

    private static ExtImpEskimi givenExtImpEskimi(Integer placementId) {
        return ExtImpEskimi.of(placementId, null, null, null, null, null, null);
    }

    private static ExtImpEskimi givenExtImpBattr(List<Integer> battr) {
        return ExtImpEskimi.of(1, null, null, null, null, null, battr);
    }

    private static ExtImpEskimi givenExtImpBidFloor(BigDecimal bidFloor, String bidFloorCur) {
        return ExtImpEskimi.of(1, bidFloor, bidFloorCur, null, null, null, null);
    }

    private static ExtImpEskimi givenExtImpBlocked(List<String> bcat, List<String> badv, List<String> bapp) {
        return ExtImpEskimi.of(1, null, null, bcat, badv, bapp, null);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    @SafeVarargs
    private BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(Arrays.stream(bidCustomizers)
                                .map(bidCustomizer -> bidCustomizer.apply(Bid.builder()).build())
                                .toList())
                        .build()))
                .build();
    }
}
