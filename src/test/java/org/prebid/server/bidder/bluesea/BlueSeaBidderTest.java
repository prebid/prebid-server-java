package org.prebid.server.bidder.bluesea;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.bluesea.ExtImpBlueSea;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class BlueSeaBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final BlueSeaBidder target = new BlueSeaBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BlueSeaBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                requestBuilder -> requestBuilder.imp(Arrays.asList(
                        givenImp(identity()),
                        Imp.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBlueSea.of("testPubId", "testToken"))))
                                .build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
    }

    @Test
    public void makeHttpRequestsShouldMakeCorrectUrls() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                requestBuilder -> requestBuilder.imp(Arrays.asList(
                        givenImp(identity()),
                        Imp.builder()
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpBlueSea.of("testPubId2", "testToken2"))))
                                .build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com?pubid=testPubId&token=testToken",
                        "https://test.endpoint.com?pubid=testPubId2&token=testToken2");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().getFirst().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

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
    public void makeBidsShouldReturnBannerBidIfMediaTypeIsBanner() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.put("mediatype", "banner");
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder()
                        .ext(bidExt)
                        .build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().ext(bidExt).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMediaTypeIsVideo() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.put("mediatype", "video");
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder()
                        .ext(bidExt)
                        .build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().ext(bidExt).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMediaTypeIsNative() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.put("mediatype", "native");
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder()
                        .ext(bidExt)
                        .build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().ext(bidExt).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorsForBidsThatDoesNotContainMediaType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder()
                        .id("789")
                        .ext(bidExt)
                        .build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Missing bid media type in bid with id: 789");
    }

    @Test
    public void makeBidsShouldReturnErrorsForBidsThatContainUnknownMediaType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.put("mediatype", "unknownType");
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder()
                        .id("789")
                        .ext(bidExt)
                        .build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Unknown bid type: unknownType, in bid with id: 789");
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {

        return requestCustomizer.apply(BidRequest.builder().imp(singletonList(givenImp(impCustomizer)))).build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("123"))
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBlueSea.of("testPubId", "testToken"))))
                .build();
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(List.of(bids))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body), null);
    }
}
