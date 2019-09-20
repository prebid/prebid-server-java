package org.prebid.server.bidder.engagebdr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
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
import org.prebid.server.proto.openrtb.ext.request.engagebdr.ExtImpEngagebdr;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class EngagebdrBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private EngagebdrBidder engagebdrBidder;

    @Before
    public void setUp() {
        engagebdrBidder = new EngagebdrBidder(ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EngagebdrBidder("invalid_url"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpContainsAudio() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .audio(Audio.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = engagebdrBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).isEqualTo("Ignoring imp id=impId, invalid MediaType" +
                " EngageBDR only supports Banner, Video and Native");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = engagebdrBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Ignoring imp id=null, error while decoding"
                + " impExt, err");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtSspidIsBlankCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEngagebdr.of(""))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = engagebdrBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Ignoring imp id=null, no sspid present");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsForImps() {
        // given
        final Imp impWithBlankSspid = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEngagebdr.of(""))))
                .build();
        final Imp impWithInvalidSspid = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                .build();
        final Imp audioImp = Imp.builder().audio(Audio.builder().build()).build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(impWithInvalidSspid, impWithBlankSspid, audioImp, audioImp, impWithBlankSspid))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = engagebdrBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(5);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Ignoring imp id=null, error while decoding"
                + " impExt, err");
        assertThat(result.getErrors().get(1).getMessage()).startsWith("Ignoring imp id=null, no sspid present");
        assertThat(result.getErrors().get(2).getMessage()).isEqualTo("Ignoring imp id=null, invalid MediaType" +
                " EngageBDR only supports Banner, Video and Native");
        assertThat(result.getErrors().get(3).getMessage()).isEqualTo("Ignoring imp id=null, invalid MediaType" +
                " EngageBDR only supports Banner, Video and Native");
        assertThat(result.getErrors().get(4).getMessage()).startsWith("Ignoring imp id=null, no sspid present");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSendImpsToUrlsBySspid() {
        // given
        final Imp firstimpWithSspidOne = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEngagebdr.of("sspid1"))))
                .build();
        final Imp secondImpWithSspidOne = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEngagebdr.of("sspid1"))))
                .build();
        final Imp impWithSspidTwo = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEngagebdr.of("sspid2"))))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstimpWithSspidOne, secondImpWithSspidOne, impWithSspidTwo))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = engagebdrBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedSspidOneBidRequest = bidRequest.toBuilder()
                .imp(Arrays.asList(firstimpWithSspidOne, secondImpWithSspidOne))
                .build();
        final BidRequest expectedSspidTwoBidRequest = bidRequest.toBuilder()
                .imp(Arrays.asList(impWithSspidTwo))
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(expectedSspidOneBidRequest, expectedSspidTwoBidRequest);
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getUri)
                .containsOnly("https://test.endpoint.com?zoneid=sspid1", "https://test.endpoint.com?zoneid=sspid2");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = engagebdrBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = engagebdrBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = engagebdrBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerWhenNoTypeProvided() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").build()))
                .build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = engagebdrBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnXnativeWhenXnativeProvided() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().xNative(Native.builder().build()).id("123").build()))
                .build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = engagebdrBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoWhenVideoProvided() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build()).id("123").build()))
                .build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = engagebdrBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(engagebdrBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
