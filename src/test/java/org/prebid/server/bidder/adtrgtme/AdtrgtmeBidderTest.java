package org.prebid.server.bidder.adtrgtme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adrino.ExtImpAdrino;
import org.prebid.server.proto.openrtb.ext.request.adtrgtme.ExtImpAdtrgtme;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class AdtrgtmeBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://z.cdn.adtarget.market/ssp";

    private final AdtrgtmeBidder target = new AdtrgtmeBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectMethodUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("123"));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(String.format("%s?s=%d&prebid", ENDPOINT_URL, 123));
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("123"));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .allSatisfy(httpRequest -> assertThat(httpRequest.getHeaders())
                        .extracting(Map.Entry::getKey, Map.Entry::getValue)
                        .containsExactly(
                                tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                                tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                                tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5")));
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBody() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("123"));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedBidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(null).id("123"));
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getPayload()).isEqualTo(expectedBidRequest);
            assertThat(httpRequest.getBody()).isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest));
        });
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedUrlWithSiteId() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("id"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://z.cdn.adtarget.market/ssp?s=123&prebid");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("ext.bidder not provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdtrgtmeBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldSplitRequestIntoMultipleRequests() {
        // given
        final BidRequest bidRequest = BidRequest.builder().site(Site.builder().id("site_id").build())
                .imp(asList(givenImp(impBuilder -> impBuilder.xNative(Native.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdrino.of("test"))))),
                        givenImp(impBuilder -> impBuilder.xNative(Native.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdrino.of("test"))))))).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("123"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsFromDifferentSeatBidsInResponse() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(asList(
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId1").impid("impId1").build()))
                                .build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId2").impid("impId2").build()))
                                .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").banner(Banner.builder().build()).build(),
                        Imp.builder().id("impId2").banner(Banner.builder().build()).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("bidId1", "bidId2");
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "{");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(bidderError.getMessage()).startsWith("Failed to decode:");
        });
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfBanner() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .video(Video.builder().build())
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse(
                        String.format("Unsupported bidtype for bid: \"%s\"", "123")));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impModifier) {
        return impModifier.apply(Imp.builder()
                        .banner(Banner.builder().h(150).w(300).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtrgtme.of(123)))))
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

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impModifier) {
        return givenBidRequest(impModifier, identity());
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<Imp.ImpBuilder> impModifier,
            UnaryOperator<BidRequest.BidRequestBuilder> requestModifier) {

        return requestModifier.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impModifier))))
                .build();
    }
}
