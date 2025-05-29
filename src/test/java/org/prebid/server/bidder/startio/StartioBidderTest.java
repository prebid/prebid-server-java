package org.prebid.server.bidder.startio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
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
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.response.BidType;
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

public class StartioBidderTest extends VertxTest {

    private StartioBidder target;

    @BeforeEach
    public void setUp() {
        target = new StartioBidder("http://test.endpoint.com", jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new StartioBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoAppOrSiteSpecified() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestCustomizer -> requestCustomizer.app(null).site(null),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).contains(
                BidderError.badInput("Bidder requires either app.id or site.id to be specified."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoAppOrSiteIdSpecified() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestCustomizer -> requestCustomizer
                        .app(App.builder().id(null).build())
                        .site(Site.builder().id(null).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).contains(
                BidderError.badInput("Bidder requires either app.id or site.id to be specified."));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestIfSiteIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestCustomizer -> requestCustomizer.app(null)
                        .site(Site.builder().id("siteId").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForUnsupportedCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestCustomizer -> requestCustomizer.cur(List.of("EUR")),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).contains(
                BidderError.badInput("Unsupported currency, bidder only accepts USD."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpressionContainsOnlyAudio() {
        // given
        final Audio audio = Audio.builder().mimes(List.of("audio/mp3")).build();
        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder.id("Imp01").banner(null).video(null).xNative(null).audio(audio));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).contains(
                BidderError.badInput("imp[0]: Unsupported media type, bidder does not support audio."));
    }

    @Test
    public void makeHttpRequestsShouldRemoveAudioFromImpressionIfItContainsMultipleMediaTypes() {

        // given
        final Audio audio = Audio.builder().mimes(List.of("audio/mp3")).build();
        final Banner banner = Banner.builder().build();
        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder.id("Imp01").audio(audio).banner(banner));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .allSatisfy(impression -> {
                    assertThat(impression.getAudio()).isEqualTo(null);
                    assertThat(impression.getBanner()).isEqualTo(banner);
                });
    }

    @Test
    public void makeHttpRequestsShouldSplitRequestByImpressions() {
        // given
        final Banner banner = Banner.builder().w(100).h(100).build();
        final Video video = Video.builder().w(100).h(200).build();
        final Native nativeImp = Native.builder().request("request").build();
        final Imp imp1 = givenImp(impBuilder -> impBuilder.id("imp1").banner(banner));
        final Imp imp2 = givenImp(impBuilder -> impBuilder.id("imp2").video(video));
        final Imp imp3 = givenImp(impBuilder -> impBuilder.id("imp3").xNative(nativeImp));
        final BidRequest bidRequest = givenBidRequest(
                requestCustomizer -> requestCustomizer.imp(List.of(imp1, imp2, imp3)),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(3)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .allSatisfy(imps -> assertThat(imps).hasSize(1))
                .extracting(List::getFirst)
                .satisfiesExactlyInAnyOrder(
                        impression -> assertThat(impression).isEqualTo(imp1),
                        impression -> assertThat(impression).isEqualTo(imp2),
                        impression -> assertThat(impression).isEqualTo(imp3));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
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
    public void makeBidsShouldReportErrorAndSkipBidIfCannotParseBidType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                givenBidResponse(
                        givenBid("001", BidType.banner),
                        givenBid("002", BidType.video),
                        givenBid("003", BidType.xNative),
                        givenBid("004", BidType.audio),
                        givenBid("005", BidType.banner).toBuilder().ext(null).build(),
                        givenBid("006", BidType.banner).toBuilder().ext(
                                mapper.createObjectNode().put("prebid", false)).build(),
                        givenBid("007", BidType.banner).toBuilder().ext(
                                mapper.createObjectNode().set("prebid",
                                        mapper.createObjectNode().put("type", "not a banner"))).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(4).allSatisfy(
                error -> assertThat(
                        error.getMessage()).startsWith("Failed to parse bid media type for impression"));
        assertThat(result.getValue()).hasSize(3).allSatisfy(
                bid -> assertThat(bid.getBid().getImpid()).isIn(List.of("001", "002", "003")));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .app(App.builder().id("appId").build())
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("345").banner(Banner.builder().build())).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, BidResponse bidResponse)
            throws JsonProcessingException {
        return givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder().seatbid(singletonList(
                SeatBid.builder().bid(asList(bids)).build())).build();
    }

    private static Bid givenBid(String impid, BidType bidType) {
        return Bid.builder()
                .impid(impid)
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .put("type", bidType.getName())))
                .build();
    }

}
