package org.prebid.server.bidder.aps;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.aps.ExtImpAps;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class ApsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://s2s.prebid.bid-{{Region}}.ads.aps.amazon-adsystem.com/e/pb/bid";
    private static final String RESOLVED_ENDPOINT_URL = "https://s2s.prebid.bid-na.ads.aps.amazon-adsystem.com/e/pb/bid";
    private static final String TEST_ACCOUNT = "test-account";

    private final ApsBidder target = new ApsBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void makeHttpRequestsShouldFailWhenAccountIdIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("imp test-imp-id: the APS bidder param \"accountID\" is required");
    }

    @Test
    public void makeHttpRequestsShouldRejectImpsWithMismatchedAccountIds() {
        // given
        final Imp imp1 = givenImp("imp-1", impBuilder -> impBuilder
                .banner(Banner.builder().w(300).h(250).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAps.of("acct-1", null)))));
        final Imp imp2 = givenImp("imp-2", impBuilder -> impBuilder
                .banner(Banner.builder().w(300).h(250).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAps.of("acct-2", null)))));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then the whole request is rejected; no request is fired under a partial account set.
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly(
                        "imp imp-2: all imps in a request must use the same APS accountID (got acct-1 and acct-2)");
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(RESOLVED_ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldAppendDebugModeToUrlWhenTestModeSet() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.test(1), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(RESOLVED_ENDPOINT_URL + "?amzn_debug_mode=1");
    }

    @Test
    public void makeHttpRequestsShouldRejectRegionOutsideAllowlist() {
        // given a region outside {na, eu, fe} — including host-injection attempts
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAps.of(TEST_ACCOUNT, "na.evil.com/")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then it is rejected before reaching the endpoint host substitution (host-injection guard).
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("imp test-imp-id: invalid APS region \"na.evil.com/\"");
    }

    @Test
    public void makeHttpRequestsShouldResolveEndpointForEuRegion() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAps.of(TEST_ACCOUNT, "eu")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then eu is a supported region and fills the endpoint macro.
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://s2s.prebid.bid-eu.ads.aps.amazon-adsystem.com/e/pb/bid");
    }

    @Test
    public void makeHttpRequestsShouldResolveRegionMacroForExplicitNa() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAps.of(TEST_ACCOUNT, "na")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(RESOLVED_ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldSetHostAccountAndSdkInExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .allSatisfy(payload -> {
                    assertThat(payload.getExt().getProperty("account").asText()).isEqualTo(TEST_ACCOUNT);
                    final JsonNode sdk = payload.getExt().getProperty("sdk");
                    assertThat(sdk.get("version").asText()).isEqualTo("1.0.0");
                    assertThat(sdk.get("source").asText()).isEqualTo("prebid-server");
                });
    }

    @Test
    public void makeHttpRequestsShouldDefaultCurrencyToUsd() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getCur)
                .containsExactly(singletonList("USD"));
    }

    @Test
    public void makeHttpRequestsShouldStripSensitiveUserDataAndDeviceGeo() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                .user(User.builder()
                        .gender("M")
                        .yob(1990)
                        .customdata("data")
                        .geo(Geo.builder().lat(1.0f).lon(2.0f).build())
                        .build())
                .device(Device.builder()
                        .geo(Geo.builder().lat(3.0f).lon(4.0f).country("USA").build())
                        .build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .allSatisfy(payload -> {
                    assertThat(payload.getUser().getGender()).isNull();
                    assertThat(payload.getUser().getYob()).isNull();
                    assertThat(payload.getUser().getCustomdata()).isNull();
                    assertThat(payload.getUser().getGeo()).isNull();
                    assertThat(payload.getDevice().getGeo().getLat()).isNull();
                    assertThat(payload.getDevice().getGeo().getLon()).isNull();
                    assertThat(payload.getDevice().getGeo().getCountry()).isEqualTo("USA");
                });
    }

    @Test
    public void makeHttpRequestsShouldBackfillBannerSizeFromFirstFormat() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder()
                .format(List.of(Format.builder().w(300).h(250).build()))
                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .allSatisfy(bannerObj -> {
                    assertThat(bannerObj.getW()).isEqualTo(300);
                    assertThat(bannerObj.getH()).isEqualTo(250);
                });
    }

    @Test
    public void makeHttpRequestsShouldOverwriteBothBannerDimsFromFirstFormatWhenOneMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder()
                .w(300)
                .format(List.of(Format.builder().w(728).h(90).build()))
                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then, matching the Prebid.js APS adapter, both dims are backfilled from format[0]
        // unless both were already set.
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .allSatisfy(bannerObj -> {
                    assertThat(bannerObj.getW()).isEqualTo(728);
                    assertThat(bannerObj.getH()).isEqualTo(90);
                });
    }

    @Test
    public void makeHttpRequestsShouldPreserveImpExtBidderBlock() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .allSatisfy(imp -> assertThat(imp.getExt().has("bidder")).isTrue());
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForAppRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder().app(App.builder().bundle("com.example.app").build()).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).contains("web (site) inventory only");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnSingleRequestForMultipleImps() {
        // given
        final Imp bannerImp = givenImp("slot-banner",
                impBuilder -> impBuilder.banner(Banner.builder().w(300).h(250).build()));
        final Imp videoImp = givenImp("slot-video",
                impBuilder -> impBuilder.video(Video.builder().mimes(List.of("video/mp4")).build()));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(bannerImp, videoImp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .allSatisfy(payload -> {
                    assertThat(payload.getImp()).extracting(Imp::getId)
                            .containsExactly("slot-banner", "slot-video");
                    assertThat(payload.getExt().getProperty("account").asText()).isEqualTo(TEST_ACCOUNT);
                });
    }

    @Test
    public void makeHttpRequestsShouldForwardServiceableImpWhenAnotherIsDroppedForUnsupportedMedia() {
        // given
        final Imp nativeImp = givenImp("imp-native",
                impBuilder -> impBuilder.xNative(Native.builder().request("{}").build()));
        final Imp bannerImp = givenImp("imp-banner",
                impBuilder -> impBuilder.banner(Banner.builder().w(300).h(250).build()));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(nativeImp, bannerImp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .allSatisfy(payload -> {
                    assertThat(payload.getImp()).extracting(Imp::getId).containsExactly("imp-banner");
                    assertThat(payload.getExt().getProperty("account").asText()).isEqualTo(TEST_ACCOUNT);
                });
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).contains("only banner and video");
                });
    }

    @Test
    public void makeHttpRequestsShouldRejectImpWithoutBannerOrVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp("imp-1",
                        impBuilder -> impBuilder.xNative(Native.builder().request("{}").build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).contains("only banner and video");
                });
    }

    @Test
    public void makeHttpRequestsShouldStripAudioAndNativeFromImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp("imp-1", impBuilder -> impBuilder
                        .banner(Banner.builder().w(300).h(250).build())
                        .audio(Audio.builder().mimes(List.of("audio/mp4")).build())
                        .xNative(Native.builder().request("{}").build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .allSatisfy(imp -> {
                    assertThat(imp.getBanner()).isNotNull();
                    assertThat(imp.getAudio()).isNull();
                    assertThat(imp.getXNative()).isNull();
                });
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error ->
                        assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws Exception {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidForMtype1() throws Exception {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bid -> bid.impid("test-imp-id").mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBidForMtype2() throws Exception {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bid -> bid.impid("test-imp-id").mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(video);
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedMtype() throws Exception {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bid -> bid.impid("test-imp-id").mtype(3))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).contains("Unsupported MType");
                });
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(impCustomizer.apply(
                                        Imp.builder()
                                                .id("test-imp-id")
                                                .banner(Banner.builder().w(300).h(250).build())
                                                .ext(givenImpExt()))
                                .build())))
                .build();
    }

    private static Imp givenImp(String impId, UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id(impId)
                        .ext(givenImpExt()))
                .build();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode givenImpExt() {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpAps.of(TEST_ACCOUNT, null)));
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
