package org.prebid.server.bidder.adoppler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adoppler.model.AdopplerResponseAdsExt;
import org.prebid.server.bidder.adoppler.model.AdopplerResponseExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adoppler.ExtImpAdoppler;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class AdopplerBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://{{AccountID}}.test.com/some/path/{{AdUnit}}";

    private AdopplerBidder adopplerBidder;

    @Before
    public void setUp() {
        adopplerBidder = new AdopplerBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdopplerBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdoppler.of(null, null)))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = adopplerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("adunit parameter is required for adoppler bidder"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adopplerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("http://clientId.test.com/some/path/adUnit");
    }

    @Test
    public void makeHttpRequestsShouldCreateUrlWithDefaultAppParamIfClientIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdoppler.of("adUnit", "")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adopplerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("http://app.test.com/some/path/adUnit");
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adopplerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"),
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidIdEmpty() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123")
                        .banner(Banner.builder().build())
                        .build()))
                .build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid(null))));

        // when
        final Result<List<BidderBid>> result = adopplerBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("unknown impId: null"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfExtEmpty() throws JsonProcessingException {
        // given
        final Imp imp = Imp.builder().id("impId").video(Video.builder().build()).build();
        final BidRequest bidRequest = BidRequest.builder().imp(Collections.singletonList(imp)).build();
        final ObjectNode ext = mapper.valueToTree(AdopplerResponseExt.of(AdopplerResponseAdsExt.of(null)));
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder
                        .id("321")
                        .impid("impId")
                        .ext(mapper.valueToTree(ext)))));

        // when
        final Result<List<BidderBid>> result = adopplerBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("$.seatbid.bid.ext.ads.video required"));
        assertThat(result.getValue()).isEmpty();
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
                .banner(Banner.builder().id("banner_id").build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdoppler.of("adUnit", "clientId")))))
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
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
