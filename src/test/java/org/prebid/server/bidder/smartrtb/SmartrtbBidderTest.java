package org.prebid.server.bidder.smartrtb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
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
import org.prebid.server.bidder.smartrtb.model.SmartrtbResponseExt;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.smartrtb.ExtImpSmartrtb;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class SmartrtbBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private SmartrtbBidder smartrtbBidder;

    @Before
    public void setUp() {
        smartrtbBidder = new SmartrtbBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SmartrtbBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldSkipInvalidImpressionAndAddError() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(identity()),
                        givenImp(impBuilder -> impBuilder.banner(null).xNative(Native.builder().build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartrtbBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "SmartRTB only supports banner and video"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartrtbBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(1).getMessage()).startsWith("Cannot infer publisher ID from bid ext");
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartrtbBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo(ENDPOINT_URL + "publisherID");
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedRequestUrlAndDefaultHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartrtbBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsOnly("https://test.endpoint.com/publisherID");
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("x-openrtb-version", "2.5"),
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "false");

        // when
        final Result<List<BidderBid>> result = smartrtbBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));
        // when
        final Result<List<BidderBid>> result = smartrtbBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsOnly(
                BidderError.badServerResponse("Invalid bid extension from endpoint."));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnTypeBannerWhenResponseExtCreativeTypeIsBanner() throws JsonProcessingException {
        // given
        final ObjectNode ext = mapper.valueToTree(SmartrtbResponseExt.of("BANNER"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.ext(ext))));

        // when
        final Result<List<BidderBid>> result = smartrtbBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().ext(null).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnTypeBannerWhenResponseExtCreativeTypeEmpty() throws JsonProcessingException {
        // given
        final ObjectNode ext = mapper.valueToTree(SmartrtbResponseExt.of("wrong type"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.ext(ext))));

        // when
        final Result<List<BidderBid>> result = smartrtbBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsOnly(
                BidderError.badServerResponse("Unsupported creative type wrong type."));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(smartrtbBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSmartrtb.of("publisherID", "123", "Zone ID is empty", true)))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
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
