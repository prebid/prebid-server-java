package org.prebid.server.bidder.consumable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.consumable.model.request.ConsumableDiag;
import org.prebid.server.bidder.consumable.model.response.ConsumableBidResponse;
import org.prebid.server.bidder.consumable.model.response.ConsumableExtBidResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.consumable.ExtImpConsumable;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class ConsumableBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://exchange.org/";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    private ConsumableBidder target;

    @Before
    public void setUp() {
        target = new ConsumableBidder(ENDPOINT_URL, prebidVersionProvider, jacksonMapper);
        given(prebidVersionProvider.getNameVersionRecord()).willReturn(null);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ConsumableBidder("invalid_url", prebidVersionProvider, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
    }

    @Test
    public void makeHttpRequestsShouldModifyImpExtWithSiteIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt(11, 32, 42, "cnsmbl-audio-728x90-slider", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .hasSize(4)
                .allSatisfy(ext -> assertThat(ext.get("networkId")).isEqualTo(TextNode.valueOf("11")))
                .allSatisfy(ext -> assertThat(ext.get("siteId")).isEqualTo(TextNode.valueOf("32")))
                .allSatisfy(ext -> assertThat(ext.get("unitId")).isEqualTo(TextNode.valueOf("42")))
                .allSatisfy(ext -> assertThat(ext.get("unitName")).isEqualTo(TextNode.valueOf("cnsmbl-audio-728x90-slider")));
    }

    @Test
    public void makeHttpRequestsShouldReturnConsumableDiagWithPbjsVersionIfRequestExtPrebidChannelVersionProvided() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(givenExtRequest("pbjsv")),
                singletonList(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("consumablediag"))
                .extracting(diagNode -> mapper.treeToValue(diagNode, ConsumableDiag.class))
                .containsExactly(ConsumableDiag.of(null, "pbjsv"));
    }

    @Test
    public void makeHttpRequestsShouldReturnConsumableDiagWithPbsVersion() {
        // given
        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbsv");
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("consumablediag"))
                .extracting(diagNode -> mapper.treeToValue(diagNode, ConsumableDiag.class))
                .containsExactly(ConsumableDiag.of("pbsv", null));
    }

    @Test
    public void makeHttpRequestsShouldNotCreateRequestSiteWhenImpExtSiteIdPresentAndSiteIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt("site1", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldModifyRequestAppWithPublisherAndSetIdWhenImpExtSiteIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.app(App.builder().build()),
                singletonList(impBuilder -> impBuilder.ext(givenImpExt("site1", null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final App expectedApp = App.builder()
                .publisher(Publisher.builder().id("site1").build())
                .build();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .containsExactly(expectedApp);
    }

    @Test
    public void makeHttpRequestsShouldNotCreateRequestAppWhenImpExtSiteIdPresentAndSiteIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt("site1", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidderResponseShouldReturnAudioBidIfAudioIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.audio, "EUR"));
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfImpNotMatched() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("489"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getBids()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Unmatched impression id 489"));
    }

    @Test
    public void makeBidderResponseShouldReturnBidWithVideoExt() throws JsonProcessingException {
        // given
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .ext(mapper.valueToTree(ExtBidPrebid.builder()
                                                .video(ExtBidPrebidVideo.of(1, null))
                                                .build())))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(node -> mapper.treeToValue(node, ExtBidPrebid.class))
                .extracting(ExtBidPrebid::getVideo)
                .extracting(ExtBidPrebidVideo::getDuration, ExtBidPrebidVideo::getPrimaryCategory)
                .containsExactly(tuple(1, null));
    }


    @Test
    public void makeBidderResponseShouldReturnBannerBidIfMTypeIsOne() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(1))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidderResponseShouldReturnVideoBidIfMTypeIsTwo() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(2))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidderResponseShouldReturnAudioBidIfMTypeIsThree() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(3))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.audio);
    }

    @Test
    public void makeBidderResponseShouldReturnNativeBidIfMTypeIsFour() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(4))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
    }

    @Test
    public void makeBidderResponseShouldReturnCorrectTypeExtPrebidTypeInResponse() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .ext(mapper.createObjectNode()
                                                .set("prebid", mapper.createObjectNode().put("type", "video"))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidderResponseShouldReturnFledgeAuctionConfig() throws JsonProcessingException {
        // given
        final String impId = "imp_id";
        final BidResponse bidResponse = givenBidResponse(bidBuilder -> bidBuilder.impid(impId).mtype(1));
        final ObjectNode fledgeAuctionConfig = mapper.createObjectNode();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder().id(impId).build()))
                .build();
        final ConsumableBidResponse bidResponseWithFledge = ConsumableBidResponse.builder()
                .cur(bidResponse.getCur())
                .seatbid(bidResponse.getSeatbid())
                .ext(ConsumableExtBidResponse.of(Map.of(impId, fledgeAuctionConfig)))
                .build();
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponseWithFledge));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .containsOnly(BidderBid.of(Bid.builder().impid(impId).mtype(1).build(), banner, bidResponse.getCur()));
        final FledgeAuctionConfig expectedFledge = FledgeAuctionConfig.builder()
                .impId(impId)
                .config(fledgeAuctionConfig)
                .build();
        assertThat(result.getFledgeAuctionConfigs()).containsExactly(expectedFledge);
    }

    private static ExtRequest givenExtRequest(String pbjsv) {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .channel(ExtRequestPrebidChannel.of("pbjs", pbjsv))
                .build());
    }

    private static ObjectNode givenImpExt(Integer networkId, Integer siteId, Integer unitId, String unitName, String placementId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpConsumable.of(networkId, siteId, unitId, unitName, placementId)));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return givenBidRequest(identity(), List.of(impCustomizers));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<UnaryOperator<Imp.ImpBuilder>> impCustomizers) {

        return bidRequestCustomizer.apply(
                        BidRequest.builder()
                                .imp(impCustomizers.stream()
                                        .map(ConsumableBidderTest::givenImp)
                                        .toList())).build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpConsumable.of(SITE_ID, null, null)))))
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
