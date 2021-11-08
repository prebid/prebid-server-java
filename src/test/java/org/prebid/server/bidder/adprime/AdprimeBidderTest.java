package org.prebid.server.bidder.adprime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.adprime.ExtImpAdprime;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AdprimeBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private AdprimeBidder adprimeBidder;

    @Before
    public void setUp() {
        adprimeBidder = new AdprimeBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdprimeBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequestWithDefaultBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adprimeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Unable to decode the impression ext for id");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsWithNoKeywordsAndAudiencesReturnsNullSiteAndUser() {
        // given
        final BidRequest bidRequest = givenBidRequestWithDefaultBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpAdprime.of("otherTagId",
                                null,
                                null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adprimeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getSite)
                .containsNull();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getUser)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsWithSiteUserAndAudiencesShouldReturnUserWithCustomData() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestCustomizer -> bidRequestCustomizer
                        .site(Site.builder().build())
                        .user(User.builder().build()),
                singletonList(impCustomizer -> impCustomizer.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpAdprime.of("otherTagId",
                                null,
                                List.of("audience1")))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adprimeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .extracting(User::getCustomdata)
                .containsExactly("audience1");
    }

    @Test
    public void makeHttpRequestsWithNoUserShouldReturnNewUserWithAudiences() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestCustomizer -> bidRequestCustomizer
                        .site(Site.builder().build()),
                singletonList(impCustomizer -> impCustomizer.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpAdprime.of("otherTagId",
                                null,
                                List.of("audience1")))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adprimeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .extracting(User::getCustomdata)
                .containsExactly("audience1");
    }

    @Test
    public void makeHttpRequestsWithSiteAndKeywordsShouldReturnSiteWithKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestCustomizer -> bidRequestCustomizer
                        .site(Site.builder().build()),
                singletonList(impCustomizer -> impCustomizer.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpAdprime.of("otherTagId",
                                List.of("keyword1"),
                                null))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adprimeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getKeywords)
                .containsExactly("keyword1");
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestCustomizer -> bidRequestCustomizer
                        .site(Site.builder().keywords("keyword1,keyword2").build())
                        .user(User.builder().customdata("audience1,audience2").build()),
                singletonList(impCustomizer -> impCustomizer.tagid("someTagId")));
        final ObjectNode modifiedImpExtBidder = createModifiedImpExtBidder();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adprimeBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedRequest = givenBidRequest(bidRequestCustomizer -> bidRequestCustomizer
                        .site(Site.builder().keywords("keyword1,keyword2").build())
                        .user(User.builder().customdata("audience1,audience2").build()),
                singletonList(impCustomizer -> impCustomizer
                        .tagid("someTagId")
                        .ext(mapper.createObjectNode().set("bidder", modifiedImpExtBidder))));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(expectedRequest);
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = givenBidRequestWithDefaultBidRequest(identity(), impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpAdprime.of("otherTagId", emptyList(), emptyList())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adprimeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(2)
                .extracting(Imp::getTagid)
                .containsExactly("someTagId", "otherTagId");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = adprimeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = adprimeBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = adprimeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfNoBidTypeIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequestWithDefaultBidRequest(impCustomizer -> impCustomizer.id("123")),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adprimeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Unknown impression type for ID:");

    }

    @Test
    public void makeBidsShouldReturnVideoBidIfNoBannerAndHasVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequestWithDefaultBidRequest(impCustomizer -> impCustomizer
                        .id("123").video(Video.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adprimeBidder.makeBids(httpCall, null);

        // then
        final BidderBid expectedBidderBid = BidderBid.of(Bid.builder().impid("123").build(), video, "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expectedBidderBid);
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfHasNativeInImpression() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequestWithDefaultBidRequest(impCustomizer -> impCustomizer
                        .id("123").xNative(Native.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adprimeBidder.makeBids(httpCall, null);

        // then
        final BidderBid expectedBidderBid = BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expectedBidderBid);
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfHasBothBannerAndVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity(),
                        singletonList(impCustomizer -> impCustomizer
                                .banner(Banner.builder().build())
                                .video(Video.builder().build()))),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adprimeBidder.makeBids(httpCall, null);

        // then
        final BidderBid expectedBidderBid = BidderBid.of(Bid.builder().impid("123").build(), banner, "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expectedBidderBid);
    }

    @Test
    public void makeBidsShouldThrowExcceptionIsNoImpressionWithIdFound() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequestWithDefaultBidRequest(identity(), identity()),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("321"))));

        // when
        final Result<List<BidderBid>> result = adprimeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to find impression");
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<Function<Imp.ImpBuilder, Imp.ImpBuilder>> impCustomizers) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(impCustomizers.stream()
                                .map(AdprimeBidderTest::givenImp)
                                .collect(Collectors.toList())))
                .build();
    }

    @SafeVarargs
    private static BidRequest givenBidRequestWithDefaultBidRequest(Function<Imp.ImpBuilder,
            Imp.ImpBuilder>... impCustomizers) {
        return givenBidRequest(identity(), asList(impCustomizers));
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdprime.of("someTagId",
                                        List.of("keyword1", "keyword2"),
                                        List.of("audience1", "audience2"))))))
                .build();
    }

    private static BidResponse givenBidResponse(
            Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body), null);
    }

    private ObjectNode createModifiedImpExtBidder() {
        final ObjectNode modifiedImpExtBidder = mapper.createObjectNode();
        modifiedImpExtBidder.set("TagID", TextNode.valueOf("someTagId"));
        modifiedImpExtBidder.set("placementId", TextNode.valueOf("someTagId"));
        return modifiedImpExtBidder;
    }
}
