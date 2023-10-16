package org.prebid.server.bidder.improvedigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExt;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExtImprovedigital;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ConsentedProvidersSettings;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.improvedigital.ExtImpImprovedigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class ImprovedigitalBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/{{PathPrefix}}";

    private final ImprovedigitalBidder target = new ImprovedigitalBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ImprovedigitalBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .id("123")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpImprovedigital.of(1234, null))))
                                .build(),
                        Imp.builder()
                                .id("456")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpImprovedigital.of(1234, 1))))
                                .build()
                ))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("123", "456");
    }

    @Test
    public void makeHttpRequestsShouldUseProperEndpoints() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .id("123")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpImprovedigital.of(1234, null))))
                                .build(),
                        Imp.builder()
                                .id("456")
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpImprovedigital.of(
                                                1234, 789
                                        ))
                                ))
                                .build()
                ))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        "https://test.endpoint.com/",
                        "https://test.endpoint.com/789/"
        );
    }

    @Test
    public void makeHttpRequestsShouldProperProcessConsentedProvidersSetting() {
        // given
        final ExtUser extUser = ExtUser.builder()
                .consentedProvidersSettings(ConsentedProvidersSettings.of("1~10.20.90"))
                .build();

        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .id("123")
                .user(User.builder().ext(extUser).build())
                .id("request_id"), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ExtUser expectedExtUser = jacksonMapper.fillExtension(extUser,
                mapper.createObjectNode().set("consented_providers_settings",
                        mapper.createObjectNode()
                                .set("consented_providers", mapper.createArrayNode().add(10).add(20).add(90))));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .containsExactly(expectedExtUser);
    }

    @Test
    public void makeHttpRequestsShouldReturnUserExtIfConsentedProvidersIsNotProvided() {
        // given
        final ExtUser extUser = ExtUser.builder()
                .consentedProvidersSettings(ConsentedProvidersSettings.of(null))
                .build();

        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.user(User.builder().ext(extUser).build()).id("request_id"), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .containsExactly(extUser);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfCannotParseConsentedProviders() {
        // given
        final ExtUser extUser = ExtUser.builder()
                .consentedProvidersSettings(ConsentedProvidersSettings.of("1~a.fv.90"))
                .build();

        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().ext(extUser).build()).id("request_id"),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage()).startsWith("Cannot deserialize value of type");
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnTwoErrorsOnTwoErrorEvents() {
        // given
        final Imp imp = Imp.builder().ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))).build();
        final BidRequest bidRequest = BidRequest.builder().imp(asList(imp, imp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenSeatBidsCountIsMoreThanOne() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(asList(SeatBid.builder()
                                .bid(singletonList(Bid.builder().build()))
                                .build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().build()))
                                .build()
                )).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse("Unexpected SeatBid! Must be only one but have: 2"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpNotFoundForBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(null)),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("321"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badServerResponse("Failed to find impression for ID: \"321\""));
        assertThat(result.getValue()).isEmpty();
    }

    private void makeBidsShouldSetProperMtypeInBidsAgainstSingleFormatImpression(
            UnaryOperator<Imp.ImpBuilder> impCustomizer,
            BidType expectedType,
            int expectedMType
    ) throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impCustomizer),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        //when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).isNotEmpty();
        assertThat(result.getValue().get(0).getBid().getMtype()).isEqualTo(expectedMType);
        assertThat(result.getValue().get(0).getType()).isEqualTo(expectedType);
    }

    @Test
    public void makeBidsShouldSetProperMtypeInBidsAgainstSingleBannerImpression() throws JsonProcessingException {
        // For banner
        makeBidsShouldSetProperMtypeInBidsAgainstSingleFormatImpression(
                impBuilder -> impBuilder.banner(Banner.builder().build()), banner, 1);
    }

    @Test
    public void makeBidsShouldSetProperMtypeInBidsAgainstSingleVideoImpression() throws JsonProcessingException {
        // For video
        makeBidsShouldSetProperMtypeInBidsAgainstSingleFormatImpression(
                impBuilder -> impBuilder.video(Video.builder().build()), video, 2);
    }

    @Test
    public void makeBidsShouldSetProperMtypeInBidsAgainstSingleAudioImpression() throws JsonProcessingException {
        // For audio
        makeBidsShouldSetProperMtypeInBidsAgainstSingleFormatImpression(
                impBuilder -> impBuilder.audio(Audio.builder().build()), audio, 3);
    }

    @Test
    public void makeBidsShouldSetProperMtypeInBidsAgainstSingleNativeImpression() throws JsonProcessingException {
        // For native
        makeBidsShouldSetProperMtypeInBidsAgainstSingleFormatImpression(
                impBuilder -> impBuilder.xNative(Native.builder().build()), xNative, 4);
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidTypeCouldNotBeDetermined() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(null)),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse(
                        "Could not determine bid type for impression with ID: \"123\""));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidDoesNotHaveMtypeForMultiFormatImpression()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                ),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse(
                        "Bid must have non-null mtype for multi format impression with ID: \"123\""));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldDetermineCorrectBidTypeForMultiFormatImpressionWhenMtypeIsSet()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().get(0).getType()).isEqualTo(banner);
    }

    @Test
    public void makeBidsShouldReturnBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder ->
                        impBuilder.banner(Banner.builder().build()).id("123")),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(1).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder
                        .video(Video.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(2).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnAudioBidIfAudioIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder
                        .audio(Audio.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(3).build(), audio, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder
                        .xNative(Native.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(4))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(4).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBidIfBidExtImprovedigitalIsNull() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(null);

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123").mtype(2).ext(mapper.valueToTree(bidExt)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .mtype(2)
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpNotFoundForId() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("456"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Failed to find impression for ID: \"456\""));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldNotPopulateDealIdWhenLineItemIsMissing() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(
                ImprovedigitalBidExtImprovedigital
                        .builder()
                        .buyingType("classic")
                        .build());

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123").mtype(2).ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .mtype(2)
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    @Test
    public void makeBidsShouldPopulateDealIdForCampaign() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(
                ImprovedigitalBidExtImprovedigital
                        .builder()
                        .lineItemId(2222222)
                        .buyingType("classic")
                        .build());

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123")
                                .mtype(2)
                                .ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .mtype(2)
                                .dealid("2222222")
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    @Test
    public void makeBidsShouldPopulateDealIdForDeal() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(
                ImprovedigitalBidExtImprovedigital
                        .builder()
                        .lineItemId(2222222)
                        .buyingType("deal")
                        .build());

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123")
                                .mtype(2)
                                .ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .dealid("2222222")
                                .mtype(2)
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    @Test
    public void makeBidsShouldNotPopulateDealIdForRtb() throws JsonProcessingException {
        // given
        final ImprovedigitalBidExt bidExt = ImprovedigitalBidExt.of(
                ImprovedigitalBidExtImprovedigital
                        .builder()
                        .lineItemId(2222222)
                        .buyingType("rtb")
                        .build());

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123")
                                .mtype(2)
                                .ext(mapper.valueToTree(bidExt))))
        );

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .impid("123")
                                .mtype(2)
                                .ext(mapper.valueToTree(bidExt)).build(),
                        video,
                        "USD"));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpImprovedigital.of(1, null)))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
