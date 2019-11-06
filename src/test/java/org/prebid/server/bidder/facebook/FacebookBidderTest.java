package org.prebid.server.bidder.facebook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.facebook.proto.FacebookExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.facebook.ExtImpFacebook;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.tuple;

public class FacebookBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test/auction";
    private static final String NON_SECURED_ENDPOINT_URL = "http://test/auction";
    private static final String PLATFORM_ID = "101";

    private FacebookBidder facebookBidder;

    @Before
    public void setUp() {
        facebookBidder = new FacebookBidder(ENDPOINT_URL, NON_SECURED_ENDPOINT_URL, PLATFORM_ID);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookBidder(null, NON_SECURED_ENDPOINT_URL, PLATFORM_ID));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookBidder(ENDPOINT_URL, null, PLATFORM_ID));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookBidder(ENDPOINT_URL, NON_SECURED_ENDPOINT_URL, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookBidder("invalid_url", NON_SECURED_ENDPOINT_URL, PLATFORM_ID))
                .withMessage("URL supplied is not valid: invalid_url");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookBidder(ENDPOINT_URL, "invalid_url", PLATFORM_ID))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldFailOnInvalidPlatformId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookBidder(ENDPOINT_URL, NON_SECURED_ENDPOINT_URL, "non-number"))
                .withMessage("Platform ID is not valid number: 'non-number'");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithExpectedHeaders() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsNotSupportedAudioMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .audio(Audio.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of("pub1_place1"))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput(
                        "audienceNetwork doesn't support native or audio Imps. Ignoring Imp ID=impId"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsNotSupportedNativeMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .xNative(Native.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of("pub1_place1"))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput(
                        "audienceNetwork doesn't support native or audio Imps. Ignoring Imp ID=impId"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsInvalidVideoCreative() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of("pub1_place1"))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).containsOnly(BidderError.badInput(
                "audienceNetwork doesn't support video type with no video data"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestDoesNotContainImpExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .w(0)
                                .h(50)
                                .build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("audienceNetwork parameters section is missing"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestImpExtFacebookPlacementIdEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .w(0)
                                .h(50)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of(""))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Missing placementId param"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestImpExtFacebookPlacementIdNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .w(0)
                                .h(50)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of(null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Missing placementId param"));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestImpExtFacebookPlacementIdIsMalformed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .w(0)
                                .h(50)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of("~malformed"))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Invalid placementId param '~malformed'"));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithHttpRequestContainingExpectedFieldsInBidRequestWithSite() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .w(0)
                                .h(50)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of("pub1_placement1"))))
                        .build()))
                .user(User.builder().ext(mapper.valueToTree(ExtUser.builder().consent("consent").build())).build())
                .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                .site(Site.builder()
                        .publisher(Publisher.builder().build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(body -> mapper.readValue(body, BidRequest.class))
                .containsExactly(BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("impId")
                                .ext(mapper.valueToTree(FacebookExt.of(101)))
                                .banner(Banner.builder()
                                        .w(0)
                                        .h(50)
                                        .build())
                                .tagid("pub1_placement1")
                                .build()))
                        .user(User.builder()
                                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                                .build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                        .site(Site.builder()
                                .publisher(Publisher.builder()
                                        .id("pub1")
                                        .build())
                                .build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithHttpRequestContainingExpectedFieldsInBidRequestWithApp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .w(0)
                                .h(50)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of("pub1_placement1"))))
                        .build()))
                .user(User.builder().ext(mapper.valueToTree(ExtUser.builder().consent("consent").build())).build())
                .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                .app(App.builder()
                        .publisher(Publisher.builder().build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(body -> mapper.readValue(body, BidRequest.class))
                .containsExactly(BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("impId")
                                .ext(mapper.valueToTree(FacebookExt.of(101)))
                                .banner(Banner.builder()
                                        .w(0)
                                        .h(50)
                                        .build())
                                .tagid("pub1_placement1")
                                .build()))
                        .user(User.builder()
                                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                                .build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                        .app(App.builder()
                                .publisher(Publisher.builder()
                                        .id("pub1")
                                        .build())
                                .build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithRequestContainingExpectedFieldsAndUpdatedLegacyBannerSizes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .w(320)
                                .h(50)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFacebook.of("pub1_placement1"))))
                        .build()))
                .app(App.builder()
                        .publisher(Publisher.builder().build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(body -> mapper.readValue(body, BidRequest.class))
                .containsExactly(BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("impId")
                                .ext(mapper.valueToTree(FacebookExt.of(101)))
                                .banner(Banner.builder()
                                        .w(0)
                                        .h(50)
                                        .build())
                                .tagid("pub1_placement1")
                                .build()))
                        .app(App.builder()
                                .publisher(Publisher.builder()
                                        .id("pub1")
                                        .build())
                                .build())
                        .build());
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, BidRequest.builder().build());

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
                                .impid("impid")
                                .dealid("dealid")
                                .adm("<div>This is an Ad</div>")
                                .build()))
                        .build()))
                .build()));

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(
                        Bid.builder()
                                .impid("impid")
                                .price(BigDecimal.ONE)
                                .dealid("dealid")
                                .w(200)
                                .h(150)
                                .adm("<div>This is an Ad</div>")
                                .build(),
                        BidType.banner, null));
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
