package org.prebid.server.bidder.thetradedesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
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
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.thetradedesk.ExtImpTheTradeDesk;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class TheTradeDeskBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/{{SupplyId}}";

    private final TheTradeDeskBidder target = new TheTradeDeskBidder(ENDPOINT_URL, jacksonMapper, "supplyid");

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TheTradeDeskBidder("invalid_url", jacksonMapper, "supplyid"));
    }

    @Test
    public void creationShouldFailWhenSupplyIdHasNumbers() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TheTradeDeskBidder(ENDPOINT_URL, jacksonMapper, "supplyid123"))
                .withMessage("SupplyId must be a simple string provided by TheTradeDesk");
    }

    @Test
    public void creationShouldFailWhenSupplyIdHasSpecificCharacters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TheTradeDeskBidder(ENDPOINT_URL, jacksonMapper, "supply_id"))
                .withMessage("SupplyId must be a simple string provided by TheTradeDesk");
    }

    @Test
    public void creationShouldFailWhenSupplyIdHasCapitalLetters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TheTradeDeskBidder(ENDPOINT_URL, jacksonMapper, "supplyId"))
                .withMessage("SupplyId must be a simple string provided by TheTradeDesk");
    }

    @Test
    public void creationShouldFailWhenSupplyIdHasWhitespaces() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TheTradeDeskBidder(ENDPOINT_URL, jacksonMapper, "supply id"))
                .withMessage("SupplyId must be a simple string provided by TheTradeDesk");
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUseConfiguredSupplyIdWhenImpExtSupplyIdIsNotProvided() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/supplyid");
    }

    @Test
    public void makeHttpRequestsShouldUseImpExtSupplyIdWhenProvided() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                imp -> imp.ext(impExt("publisher", "supplySourceId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/supplySourceId");
    }

    @Test
    public void makeHttpRequestsShouldUseFirstFoundSupplySourceId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                imp -> imp.ext(impExt("publisher", null)),
                imp -> imp.ext(impExt("publisher", "supplySourceId1")),
                imp -> imp.ext(impExt("publisher", "supplySourceId2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/supplySourceId1");
    }

    @Test
    public void makeHttpRequestsShouldHaveImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                imp -> imp.id("givenImp1"),
                imp -> imp.id("givenImp2"));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder(Set.of("givenImp1", "givenImp2"));
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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBothSupplySourceIdAndSupplyIdAreNull() {
        final TheTradeDeskBidder bidderWithNullSupplyId = new TheTradeDeskBidder(ENDPOINT_URL, jacksonMapper, null);
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                imp -> imp.ext(impExt("publisher", null)));

        final Result<List<HttpRequest<BidRequest>>> result = bidderWithNullSupplyId.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage())
                .isEqualTo("Either supplySourceId or a default endpoint must be provided");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnSiteWithExtImpPublisherWhenSiteAndAppArePresent() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .site(Site.builder().publisher(Publisher.builder().id("sitePublisher").build()).build())
                        .app(App.builder().publisher(Publisher.builder().id("appPublisher").build()).build()),
                imp -> imp.ext(impExt("newPublisher")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final BidRequest expectedRequest = givenBidRequest(
                request -> request
                        .site(Site.builder().publisher(Publisher.builder().id("newPublisher").build()).build())
                        .app(App.builder().publisher(Publisher.builder().id("appPublisher").build()).build()),
                imp -> imp.ext(impExt("newPublisher")));

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest));
    }

    @Test
    public void makeHttpRequestsShouldReturnSiteWithExtImpPublisherWhenSiteWithoutPublisherAndAppArePresent() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .site(Site.builder().publisher(null).build())
                        .app(App.builder().publisher(Publisher.builder().id("appPublisher").build()).build()),
                imp -> imp.ext(impExt("newPublisher")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final BidRequest expectedRequest = givenBidRequest(
                request -> request
                        .site(Site.builder().publisher(Publisher.builder().id("newPublisher").build()).build())
                        .app(App.builder().publisher(Publisher.builder().id("appPublisher").build()).build()),
                imp -> imp.ext(impExt("newPublisher")));

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest));
    }

    @Test
    public void makeHttpRequestsShouldReturnAppWithExtImpPublisherWhenSiteIsAbsentAndAppIsPresent() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .site(null)
                        .app(App.builder().publisher(Publisher.builder().id("appPublisher").build()).build()),
                imp -> imp.ext(impExt("newPublisher")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final BidRequest expectedRequest = givenBidRequest(
                request -> request
                        .app(App.builder().publisher(Publisher.builder().id("newPublisher").build()).build()),
                imp -> imp.ext(impExt("newPublisher")));

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest));
    }

    @Test
    public void makeHttpRequestsShouldReturnAppWithExtImpPublisherWhenAppWithoutPublisherArePresent() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .site(null)
                        .app(App.builder().publisher(null).build()),
                imp -> imp.ext(impExt("newPublisher")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final BidRequest expectedRequest = givenBidRequest(
                request -> request
                        .app(App.builder().publisher(Publisher.builder().id("newPublisher").build()).build()),
                imp -> imp.ext(impExt("newPublisher")));

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest));
    }

    @Test
    public void makeHttpRequestsShouldReturnAppWithPublisherOfTheFirstExtImp() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder().build()),
                imp -> imp.ext(impExt("newPublisher")),
                imp -> imp.ext(impExt("ignoredPublisher")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final BidRequest expectedRequest = givenBidRequest(
                request -> request.app(App.builder().publisher(Publisher.builder().id("newPublisher").build()).build()),
                imp -> imp.ext(impExt("newPublisher")),
                imp -> imp.ext(impExt("ignoredPublisher")));

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest));
    }

    @Test
    public void makeHttpRequestsShouldReturnBannerImpWithTheFirstFormat() {
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                imp -> imp.banner(Banner.builder()
                        .h(1)
                        .w(2)
                        .format(List.of(
                                Format.builder().h(11).w(22).build(),
                                Format.builder().h(111).w(222).build()))
                        .build()),
                imp -> imp.banner(null).video(Video.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final BidRequest expectedRequest = givenBidRequest(
                identity(),
                imp -> imp.banner(Banner.builder()
                        .h(11)
                        .w(22)
                        .format(List.of(
                                Format.builder().h(11).w(22).build(),
                                Format.builder().h(111).w(222).build()))
                        .build()),
                imp -> imp.video(Video.builder().build()));

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest));
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
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid':");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
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
    public void makeBidsShouldReturnxNativeBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.mtype(4).impid("123")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(4).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.mtype(1).impid("123")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(1).impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.mtype(2).impid("123")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(2).impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldThrowErrorWhenMediaTypeIsMissing() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("unsupported mtype: null"));
    }

    private String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder>... impCustomizers) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(Arrays.stream(impCustomizers).map(TheTradeDeskBidderTest::givenImp).toList()))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("123").ext(impExt("publisherId"))).build();
    }

    private static ObjectNode impExt(String publisherId) {
        return impExt(publisherId, null);
    }

    private static ObjectNode impExt(String publisherId, String supplySourceId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpTheTradeDesk.of(publisherId, supplySourceId)));
    }

}
