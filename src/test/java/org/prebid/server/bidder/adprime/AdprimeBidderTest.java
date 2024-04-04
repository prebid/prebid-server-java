package org.prebid.server.bidder.adprime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adprime.ExtImpAdprime;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AdprimeBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final AdprimeBidder target = new AdprimeBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdprimeBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final ArrayNode givenImpExt = mapper.createArrayNode();
        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer.ext(mapper.valueToTree(ExtPrebid.of(null, givenImpExt))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Unable to decode the impression ext for id");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsWithNoKeywordsAndAudiencesReturnsNullSiteAndUser() {
        // given
        final ExtImpAdprime impExt = ExtImpAdprime.of("otherTagId", null, null);
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer.ext(givenImpExt(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final ExtImpAdprime impExt = ExtImpAdprime.of("otherTagId", null, List.of("audience1"));
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer.ext(givenImpExt(impExt)))
                .toBuilder()
                .site(Site.builder().build())
                .user(User.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final ExtImpAdprime impExt = ExtImpAdprime.of("otherTagId", null, List.of("audience1"));
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer.ext(givenImpExt(impExt)))
                .toBuilder()
                .site(Site.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final ExtImpAdprime impExt = ExtImpAdprime.of("otherTagId", List.of("keyword1"), null);
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer.ext(givenImpExt(impExt)))
                .toBuilder()
                .site(Site.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer.tagid("someTagId"))
                .toBuilder()
                .site(Site.builder().keywords("keyword1,keyword2").build())
                .user(User.builder().customdata("audience1,audience2").build())
                .build();
        final ObjectNode modifiedImpExtBidder = createModifiedImpExtBidder();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedRequest = givenBidRequest(impCustomizer -> impCustomizer
                .tagid("someTagId")
                .ext(mapper.createObjectNode().set("bidder", modifiedImpExtBidder)))
                .toBuilder()
                .site(Site.builder().keywords("keyword1,keyword2").build())
                .user(User.builder().customdata("audience1,audience2").build())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(expectedRequest);
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final ExtImpAdprime impExt = ExtImpAdprime.of("otherTagId", emptyList(), emptyList());
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impCustomizer -> impCustomizer.ext(givenImpExt(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(bannerBid, banner, "USD"));

    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid videoBid = Bid.builder().impid("2").mtype(2).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(videoBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid nativeBid = Bid.builder().impid("4").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(nativeBid, xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpTypeIsNotSupported() throws JsonProcessingException {
        // given
        final Bid audioBid = Bid.builder().impid("id").mtype(3).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Unable to fetch mediaType 3 in multi-format: id"));
    }

    @SafeVarargs
    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Stream.of(impCustomizers).map(AdprimeBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdprime.of("someTagId",
                                        List.of("keyword1", "keyword2"),
                                        List.of("audience1", "audience2"))))))
                .build();
    }

    private static String givenBidResponse(Bid bid) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bid))
                        .build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(BidRequest.builder().build()).build(),
                HttpResponse.of(200, null, body), null);
    }

    private ObjectNode givenImpExt(ExtImpAdprime impExt) {
        return mapper.valueToTree(ExtPrebid.of(null, impExt));
    }

    private ObjectNode createModifiedImpExtBidder() {
        final ObjectNode modifiedImpExtBidder = mapper.createObjectNode();
        modifiedImpExtBidder.set("TagID", TextNode.valueOf("someTagId"));
        modifiedImpExtBidder.set("placementId", TextNode.valueOf("someTagId"));
        return modifiedImpExtBidder;
    }
}
