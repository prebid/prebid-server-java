package org.prebid.server.bidder.adoppler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.prebid.server.VertxTest;
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
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class AdopplerBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

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
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdoppler.of(null)))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = adopplerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("$.imp.ext.adoppler.adunit required");
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
        final Result<List<HttpRequest<BidRequest>>> result = adopplerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://test.endpoint.com/processHeaderBid/adUnit");
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedRequestUrlAndDefaultHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adopplerBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("x-openrtb-version", "2.5"),
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeBidsShouldReturnErrorIfDuplicateId() throws JsonProcessingException {
        // given
        final Imp imp1 = Imp.builder().id("impId").banner(Banner.builder().build()).build();
        final Imp imp2 = Imp.builder().id("impId").video(Video.builder().build()).build();
        final List imps = new ArrayList();
        imps.add(imp1);
        imps.add(imp2);
        BidRequest bidRequest = BidRequest.builder()
                .imp(imps)
                .build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adopplerBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("duplicate $.imp.id impId");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfEmptyImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123")
                        .banner(null)
                        .video(null)
                        .audio(null)
                        .xNative(null)
                        .build()))
                .build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest, mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = adopplerBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("one of $.imp.banner, $.imp.video, $.imp.audio and $.imp.native field required");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
        assertThat(result.getValue()).isEmpty();
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
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("unknown impid: null");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfExtEmpty() throws JsonProcessingException {
        // given
        final Imp imp = Imp.builder().id("impId").video(Video.builder().build()).build();
        final List imps = Collections.singletonList(imp);
        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();
        final ObjectNode ext = mapper.valueToTree(AdopplerResponseExt.of(null));
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder
                        .id("321")
                        .impid("impId")
                        .ext(mapper.valueToTree(ext)))));

        // when
        final Result<List<BidderBid>> result = adopplerBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("$.seatbid.bid.ext.ads.video required");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseWithNoContent() {
        // given
        final HttpCall<BidRequest> httpCall = HttpCall
                .success(null, HttpResponse.of(204, null, null), null);

        // when
        final Result<List<BidderBid>> result = adopplerBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(adopplerBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
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
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdoppler.of("adUnit")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
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
