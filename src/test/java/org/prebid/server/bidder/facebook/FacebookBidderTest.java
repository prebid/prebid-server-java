package org.prebid.server.bidder.facebook;

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
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.facebook.proto.FacebookExt;
import org.prebid.server.bidder.facebook.proto.FacebookNative;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.facebook.ExtImpFacebook;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class FacebookBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test/auction";
    private static final String PLATFORM_ID = "101";
    private static final String APP_SECRET = "6237";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    public static final String TIMEOUT_NOTIFICATION_URL_TEMPLATE = "https://url/?p=%s&a=%s&auction=%s&ortb_loss_code=2";

    private FacebookBidder facebookBidder;

    @Before
    public void setUp() {
        facebookBidder = new FacebookBidder(
                ENDPOINT_URL, PLATFORM_ID, APP_SECRET, TIMEOUT_NOTIFICATION_URL_TEMPLATE, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnBlankArguments() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new FacebookBidder(
                        ENDPOINT_URL, " ", APP_SECRET, TIMEOUT_NOTIFICATION_URL_TEMPLATE, jacksonMapper))
                .withMessageStartingWith("No facebook platform-id specified.");
        assertThatIllegalArgumentException().isThrownBy(
                () -> new FacebookBidder(
                        ENDPOINT_URL, PLATFORM_ID, " ", TIMEOUT_NOTIFICATION_URL_TEMPLATE, jacksonMapper))
                .withMessageStartingWith("No facebook app-secret specified.");
    }

    @Test
    public void creationShouldFailOnInvalidEndpoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookBidder(
                        "invalid_url", PLATFORM_ID, APP_SECRET, TIMEOUT_NOTIFICATION_URL_TEMPLATE, jacksonMapper))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenUserIsNullOrBuyerUidIsBlank() {
        // given
        final BidRequest nullUserRequest = givenBidRequest(identity(), identity(),
                requestBuilder -> requestBuilder.user(null));
        final BidRequest nullBuyerUidRequest = givenBidRequest(identity(), identity(),
                requestBuilder -> requestBuilder.user(User.builder().build()));
        final BidRequest blankBuyerUidRequest = givenBidRequest(identity(), identity(),
                requestBuilder -> requestBuilder.user(User.builder().buyeruid(" ").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> nullUserResult = facebookBidder.makeHttpRequests(nullUserRequest);
        final Result<List<HttpRequest<BidRequest>>> nullBuyerUidResult =
                facebookBidder.makeHttpRequests(nullBuyerUidRequest);
        final Result<List<HttpRequest<BidRequest>>> blankBuyerUidResult =
                facebookBidder.makeHttpRequests(blankBuyerUidRequest);

        // then
        assertThat(nullUserResult.getValue()).isEmpty();
        assertThat(nullBuyerUidResult.getValue()).isEmpty();
        assertThat(blankBuyerUidResult.getValue()).isEmpty();

        final BidderError expectedError = BidderError.badInput("Missing bidder token in 'user.buyeruid'");
        assertThat(nullUserResult.getErrors()).hasSize(1)
                .containsOnly(expectedError);
        assertThat(nullBuyerUidResult.getErrors()).hasSize(1)
                .containsOnly(expectedError);
        assertThat(blankBuyerUidResult.getErrors()).hasSize(1)
                .containsOnly(expectedError);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCannotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value of");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtPlacementIdIsNullOrBlank() {
        // given
        final BidRequest nullPlacementRequest = givenBidRequest(identity(),
                extImpFacebook -> ExtImpFacebook.of(null, null));
        final BidRequest blankPlacementRequest = givenBidRequest(identity(),
                extImpFacebook -> ExtImpFacebook.of(" ", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> nullPlacementResult =
                facebookBidder.makeHttpRequests(nullPlacementRequest);
        final Result<List<HttpRequest<BidRequest>>> blankPlacementResult =
                facebookBidder.makeHttpRequests(blankPlacementRequest);

        // then
        assertThat(nullPlacementResult.getValue()).isEmpty();
        assertThat(blankPlacementResult.getValue()).isEmpty();

        final BidderError expectedError = BidderError.badInput("Missing placementId param");
        assertThat(nullPlacementResult.getErrors()).hasSize(1)
                .containsOnly(expectedError);
        assertThat(blankPlacementResult.getErrors()).hasSize(1)
                .containsOnly(expectedError);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtPublisherIdNullOrEmptyAndPlacementHasOnlyOnePart() {
        // given
        final BidRequest nullPubRequest = givenBidRequest(identity(),
                extImpFacebook -> ExtImpFacebook.of("placementId", null));
        final BidRequest blankPubRequest = givenBidRequest(identity(),
                extImpFacebook -> ExtImpFacebook.of("placementId", " "));

        // when
        final Result<List<HttpRequest<BidRequest>>> nullPubResult = facebookBidder.makeHttpRequests(nullPubRequest);
        final Result<List<HttpRequest<BidRequest>>> blankPubResult = facebookBidder.makeHttpRequests(blankPubRequest);

        // then
        assertThat(nullPubResult.getValue()).isEmpty();
        assertThat(blankPubResult.getValue()).isEmpty();

        final BidderError expectedError = BidderError.badInput("Missing publisherId param");
        assertThat(nullPubResult.getErrors()).hasSize(1)
                .containsOnly(expectedError);
        assertThat(blankPubResult.getErrors()).hasSize(1)
                .containsOnly(expectedError);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtPlacementIdHasMoveThanTwoParts() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                extImpFacebook -> ExtImpFacebook.of("pla_cement_Id", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                BidderError.badInput("Invalid placementId param 'pla_cement_Id' and publisherId param 'null'"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpHasNoType() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(null), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #imp1 with invalid type"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpInterstitialEqOneAndImpHasNoBanner() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .instl(1)
                        .banner(null)
                        .video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #imp1: interstitial imps are only supported for banner"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpBannerHasNotSupportedHeight() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().h(10).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #imp1: only banner heights 50 and 250 are supported"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("X-Fb-Pool-Routing-Token", "bUid"));
    }

    @Test
    public void makeHttpRequestsShouldCreateSeparateRequestForEachImpAndSkipInvalidImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("req1")
                .user(User.builder().buyeruid("buid").build())
                .imp(asList(
                        givenImp(identity(), identity()),
                        givenImp(identity(), identity()),
                        givenImp(identity(), extImpFacebook -> ExtImpFacebook.of(null, null))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getUri, HttpRequest::getMethod)
                .containsOnly(tuple(ENDPOINT_URL, HttpMethod.POST));
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtPublisherIdFromPlacementIdPart() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                extImpFacebook -> ExtImpFacebook.of("newPublisher_newPlacement", null),
                requestBuilder -> requestBuilder.app(App.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .containsOnly(Publisher.builder().id("newPublisher").build());
    }

    @Test
    public void makeHttpRequestsShouldModifyImpExtAndTagIdAndMakeNoOtherChangesForAudioImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null).audio(Audio.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt, Imp::getTagid)
                .containsOnly(tuple(null, "pubId_placementId"));
    }

    @Test
    public void makeHttpRequestsShouldModifyImpBannerAsExpected() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsOnly(Banner.builder().h(50).w(-1).build());
    }

    @Test
    public void makeHttpRequestsShouldModifyImpBannerWhenImpInterstitialEqOne() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.instl(1), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsOnly(Banner.builder().h(0).w(0).build());
    }

    @Test
    public void makeHttpRequestsShouldModifyImpBannerWhenHeightPresentedInFormatAndInterstitialIsNotOne() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                        .banner(Banner.builder().w(0)
                                .format(singletonList(Format.builder().h(250).build()))
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsOnly(Banner.builder().h(250).w(-1).build());
    }

    @Test
    public void makeHttpRequestsShouldThrowErrorIfFormatHeightIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                        .banner(Banner.builder().w(0)
                                .format(singletonList(Format.builder().h(300).build()))
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #imp1: banner height required"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldModifyImpVideoAsExpected() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(null)
                        .video(Video.builder().w(400).h(500).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .containsOnly(Video.builder().h(0).w(0).build());
    }

    @Test
    public void makeHttpRequestsShouldModifyImpNativeByAddingWidthAndHeightAndRemovingRequestAndVerFields() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(null)
                        .xNative(Native.builder().request("to_be_removed").ver("3").api(singletonList(1)).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                // use payload as deserializing from body json string converts native to parent class, which
                // is not aware of child's fields
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getXNative)
                .containsOnly(FacebookNative.builder().w(-1).h(-1).api(singletonList(1)).build());

        // extra check to assure that data in body is displayed correctly
        assertThat(result.getValue().get(0).getBody())
                .contains("\"native\":{\"api\":[1],\"w\":-1,\"h\":-1},\"tagid\":\"pubId_placementId\"}");
    }

    @Test
    public void makeHttpRequestsShouldReplaceAppPublisher() {
        // given
        final Publisher publisher = Publisher.builder().id("521").name("should_be_replaced").build();
        final BidRequest appRequest = givenBidRequest(identity(), identity(),
                requestBuilder -> requestBuilder.app(App.builder().publisher(publisher).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> appResult = facebookBidder.makeHttpRequests(appRequest);

        // then
        assertThat(appResult.getErrors()).isEmpty();

        final Publisher expectedPublisher = Publisher.builder().id("pubId").build();
        assertThat(appResult.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .containsOnly(expectedPublisher);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSiteIsNotEmpty() {
        // given
        final BidRequest siteRequest = givenBidRequest(identity(), identity(),
                requestBuilder -> requestBuilder.site(Site.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> siteResult = facebookBidder.makeHttpRequests(siteRequest);

        // then
        assertThat(siteResult.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Site impressions are not supported."));
    }

    @Test
    public void makeHttpRequestsShouldChangeRequestExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = facebookBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> mapper.convertValue(request.getExt(), FacebookExt.class))
                .containsOnly(
                        FacebookExt.of("101", "bd49902da11ce0fe6258e56baa0a69c2f1395b2ff1efb30d4879ed9e2343a3f6"));
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
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidHasNullOrBlankAdm() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().id("bid1").adm(null).build(),
                Bid.builder().id("bid2").adm(" ").build());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2).containsOnly(
                BidderError.badServerResponse("Bid bid1 missing 'adm'"),
                BidderError.badServerResponse("Bid bid2 missing 'adm'"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidAdmCouldNotBeParsed() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().id("bid1").adm("invalid").build());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid'");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidAdmHasNullOrBlankBidId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().id("bid1").adm("{\"type\":\"0\"}").build(),
                Bid.builder().id("bid2").adm("{\"bid_id\":\" \"}").build());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2).containsOnly(
                BidderError.badServerResponse("bid bid1 missing 'bid_id' in 'adm'"),
                BidderError.badServerResponse("bid bid2 missing 'bid_id' in 'adm'"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenCannotDetermineBidType() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().id("bid2").impid("imp1").adm("{\"bid_id\":\"10\"}").build());

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(null), identity());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                BidderError.badServerResponse("Processing an invalid impression; cannot resolve impression type"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenNoMatchingImpFoundByBidImpId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().id("bid2").impid("imp2").adm("{\"bid_id\":\"10\"}").build());

        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse(
                        "Invalid bid imp ID imp2 does not match any imp IDs from the original bid request"));
    }

    @Test
    public void makeBidsShouldSkipInvalidBidAndAddErrorAndReturnExpectedBannerBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().impid("imp1").adm("{\"bid_id\":\"10\"}").build(),
                Bid.builder().id("bid2").adm("{\"bid_id\":\" \"}").build());

        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("bid bid2 missing 'bid_id' in 'adm'"));

        final Bid expectedBid = Bid.builder().impid("imp1").adm("{\"bid_id\":\"10\"}").adid("10").crid("10").build();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(expectedBid, BidType.banner, DEFAULT_BID_CURRENCY));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().impid("imp1").adm("{\"bid_id\":\"10\"}").build());

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null).video(Video.builder().build()), identity());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsOnly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().impid("imp1").adm("{\"bid_id\":\"10\"}").build());

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null).xNative(Native.builder().build()), identity());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsOnly(BidType.xNative);
    }

    @Test
    public void makeBidsShouldReturnAudioBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().impid("imp1").adm("{\"bid_id\":\"10\"}").build());

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null).audio(Audio.builder().build()), identity());

        // when
        final Result<List<BidderBid>> result = facebookBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsOnly(BidType.audio);
    }

    @Test
    public void makeTimeoutNotificationShouldGenerateRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity()).toBuilder()
                .app(App.builder().publisher(Publisher.builder().id("test").build()).build())
                .build();
        final HttpRequest<BidRequest> httpRequest = HttpRequest.<BidRequest>builder()
                .body(mapper.writeValueAsString(bidRequest))
                .payload(bidRequest)
                .build();

        // when
        final HttpRequest<Void> notification = facebookBidder.makeTimeoutNotification(httpRequest);

        // then
        assertThat(notification.getUri()).isEqualTo("https://url/?p=101&a=test&auction=req1&ortb_loss_code=2");
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpFacebook, ExtImpFacebook> impExtCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()
                .id("req1")
                .user(User.builder().buyeruid("bUid").build())
                .imp(singletonList(givenImp(impCustomizer, impExtCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                              Function<ExtImpFacebook, ExtImpFacebook> impExtCustomizer) {
        return givenBidRequest(impCustomizer, impExtCustomizer, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                Function<ExtImpFacebook, ExtImpFacebook> impExtCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("imp1")
                .banner(Banner.builder().h(50).format(singletonList(Format.builder().build())).build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        null, impExtCustomizer.apply(ExtImpFacebook.of("placementId", "pubId"))))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }

    private static HttpCall<BidRequest> givenHttpCall(Bid... bids) throws JsonProcessingException {
        return givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(asList(bids))
                        .build()))
                .build()));
    }
}
