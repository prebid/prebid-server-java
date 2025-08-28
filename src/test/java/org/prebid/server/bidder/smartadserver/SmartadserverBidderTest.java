package org.prebid.server.bidder.smartadserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.smartadserver.ExtImpSmartadserver;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class SmartadserverBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/path?testParam=testVal";
    private static final String SECONDARY_URL = "https://test.endpoint2.com/path?testParam=testVal";

    private final SmartadserverBidder target = new SmartadserverBidder(ENDPOINT_URL, SECONDARY_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SmartadserverBidder("invalid_url", SECONDARY_URL, jacksonMapper));
    }

    @Test
    public void creationShouldFailOnInvalidSecondaryEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SmartadserverBidder(ENDPOINT_URL, "invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Error parsing smartadserverExt parameters"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectPrimaryURLWhenProgrammaticGuaranteedIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().getFirst().getUri())
                .isEqualTo("https://test.endpoint.com/path/api/bid?testParam=testVal&callerId=5");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectSecondaryURLWhenProgrammaticGuaranteedIsTrue() {
        // given
        final ObjectNode givenImpExt = mapper.createObjectNode()
                .set("bidder", mapper.createObjectNode().put("programmaticGuaranteed", true));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(imp -> imp.ext(givenImpExt))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().getFirst().getUri())
                .isEqualTo("https://test.endpoint2.com/path/ortb?testParam=testVal");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectPrimaryURLWhenProgrammaticGuaranteedIsFalse() {
        // given
        final ObjectNode givenImpExt = mapper.createObjectNode()
                .set("bidder", mapper.createObjectNode().put("programmaticGuaranteed", false));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(imp -> imp.ext(givenImpExt))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().getFirst().getUri())
                .isEqualTo("https://test.endpoint.com/path/api/bid?testParam=testVal&callerId=5");
    }

    @Test
    public void makeHttpRequestsShouldUpdateSiteObjectIfPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .site(Site.builder()
                        .domain("www.foo.com")
                        .publisher(Publisher.builder().domain("foo.com").build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final Site expectedSite = Site.builder()
                .domain("www.foo.com")
                .publisher(Publisher.builder().domain("foo.com").id("4").build())
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsExactly(expectedSite);
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(
                        givenImp(impBuilder -> impBuilder.id("123")),
                        givenImp(impBuilder -> impBuilder.id("456"))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("123", "456");
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequestWithValidImpsOnly() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(givenImp(impBuilder -> impBuilder.id("123")),
                        Imp.builder()
                                .id("invalidImp")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Error parsing smartadserverExt parameters"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("123");
    }

    @Test
    public void makeHttpRequestsShouldModifyImpWhenProgrammaticGuaranteedIsTrueAtLeastInOneValidImp() {
        // given
        final ObjectNode givenImpExt1 = mapper.createObjectNode()
                .set("bidder", mapper.createObjectNode()
                        .put("programmaticGuaranteed", false)
                        .put("networkId", 1)
                        .put("siteId", 2)
                        .put("formatId", 3)
                        .put("pageId", 4));
        final ObjectNode givenImpExt2 = mapper.createObjectNode()
                .set("bidder", mapper.createObjectNode()
                        .put("programmaticGuaranteed", true)
                        .put("networkId", 5)
                        .put("siteId", 6)
                        .put("formatId", 7)
                        .put("pageId", 8));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(imp -> imp.id("impId1").ext(givenImpExt1)),
                        givenImp(imp -> imp.id("impId2").ext(givenImpExt2))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt1 = mapper.createObjectNode()
                .set("smartadserver", mapper.createObjectNode()
                        .put("networkId", 1)
                        .put("siteId", 2)
                        .put("formatId", 3)
                        .put("pageId", 4));
        final ObjectNode expectedImpExt2 = mapper.createObjectNode()
                .set("smartadserver", mapper.createObjectNode()
                        .put("networkId", 5)
                        .put("siteId", 6)
                        .put("formatId", 7)
                        .put("pageId", 8));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt1, expectedImpExt2);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .allMatch(error -> error.getMessage().startsWith("Failed to decode: Unrecognized token")
                        && error.getType() == BidderError.Type.bad_server_response);
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
    public void makeBidsShouldReturnBannerBidIfMarkupTypeIsBanner() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().mtype(1).build(), banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnAudioBidIfMarkupTypeIsAudio() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.mtype(3))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().mtype(3).build(), audio, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMarkupTypeIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().build(), banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMarkupTypeOutOfBounds() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.mtype(5))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().mtype(5).build(), banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfMarkupTypeIsVideo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().mtype(2).build(), video, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfMarkupTypeIsNative() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.mtype(4))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().mtype(4).build(), xNative, "EUR"));
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null,
                                ExtImpSmartadserver.of(1, 2, 3, 4, false)))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("EUR")
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
