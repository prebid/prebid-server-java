package org.prebid.server.bidder.pangle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.pangle.model.BidExt;
import org.prebid.server.bidder.pangle.model.NetworkIds;
import org.prebid.server.bidder.pangle.model.PangleBidExt;
import org.prebid.server.bidder.pangle.model.WrappedImpExtBidder;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.pangle.ExtImpPangle;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class PangleBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private PangleBidder pangleBidder;

    @Before
    public void setUp() {
        pangleBidder = new PangleBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PangleBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                requestBuilder -> requestBuilder.imp(Arrays.asList(
                        givenImp(identity()),
                        givenImp(identity()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithAdTypeSevenIfVideoIsPresentAndIsRewardedIsOne() {
        // given
        final ExtImpPrebid extPrebid = ExtImpPrebid.builder().isRewardedInventory(1).build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(extPrebid, ExtImpPangle.of(
                                "token", null, null), null, true, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt = mapper
                .valueToTree(WrappedImpExtBidder.of(extPrebid, ExtImpPangle.of("token", null, null), 7, true, null));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt);
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithAdTypeEightIfVideoIsPresentAndInstlIsOne() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .instl(1)
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", null, null), null, true, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt = mapper
                .valueToTree(WrappedImpExtBidder.of(null,
                        ExtImpPangle.of("token", null, null), 8, true, null));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt);
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithAdTypeTwoIfBannerIsPresentAndInstlIsOne() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .instl(1)
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", null, null), null, true, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt = mapper
                .valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of("token", null, null), 2, true, null));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt);
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithAdTypeOneIfBannerIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", null, null), null, true, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt = mapper
                .valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                        "token", null, null), 1, true, null));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt);
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithAdTypeFiveIfNativeRequestIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .xNative(Native.builder().request("someRequest").build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", null, null), null, true, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt = mapper
                .valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                        "token", null, null), 5, true, null));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForNotSupportedAdType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .audio(Audio.builder().build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", null, null), null, true, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("not a supported adtype"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple("TOKEN", "token"));
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(null))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badServerResponse("the bid request object is not present"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidExtIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badServerResponse("missing pangleExt/adtype in bid ext"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidExtAdTypeIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(
                        givenBidResponse(bid -> bid.ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(null)))))));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badServerResponse("missing pangleExt/adtype in bid ext"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfAdTypeIsOne() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bid ->
                        bid.ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(1)))))));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(1))))
                        .build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfAdTypeIsTwo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bid ->
                        bid.ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(2)))))));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(2))))
                        .build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfAdTypeIsFive() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bid ->
                        bid.ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(5)))))));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(5))))
                        .build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVide0BidIfAdTypeIsSeven() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bid ->
                        bid.ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(7)))))));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(7))))
                        .build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVide0BidIfAdTypeIsEight() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bid ->
                        bid.ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(8)))))));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder()
                        .ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(8))))
                        .build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorForUnrecognizedAdType() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bid ->
                        bid.ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(777)))))));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badServerResponse("unrecognized adtype in response"));
    }

    @Test
    public void makeBidsShouldReturnErrorsAndResult() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(Arrays.asList(Bid.builder().build(),
                                        Bid.builder()
                                                .ext(mapper.valueToTree(PangleBidExt.of(BidExt.of(8))))
                                                .build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = pangleBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithNetworkIds() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", "2", "2"),
                                null, true, NetworkIds.of("1", "1"))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt = mapper
                .valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                        "token", "2", "2"),
                        1, true, NetworkIds.of("2", "2")));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt);
    }

    @Test
    public void makeHttpRequestsShouldAddErrorAndSkipImpressionIfAppIdIsNotPresentAndPlacementIdIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", null, "1"),
                                null, true, NetworkIds.of("1", "1"))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldAddErrorAndSkipImpressionIfPlacementIdIsNotPresentAndAppIdIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", "1", null),
                                null, true, NetworkIds.of("1", "1"))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotUpdateImpExtWithNetworkIdsIfNetworkIdsAreAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(WrappedImpExtBidder.of(null, ExtImpPangle.of(
                                "token", null, null),
                                null, true, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pangleBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt = mapper
                .valueToTree(WrappedImpExtBidder.of(null,
                        ExtImpPangle.of("token", null, null), 1, true, null));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt);
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(impCustomizer, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123"))
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpPangle.of("token", null, null))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
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
}

