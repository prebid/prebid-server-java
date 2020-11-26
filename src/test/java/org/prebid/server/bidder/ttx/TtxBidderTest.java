package org.prebid.server.bidder.ttx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.bidder.ttx.proto.TtxImpExt;
import org.prebid.server.bidder.ttx.proto.TtxImpExtTtx;
import org.prebid.server.bidder.ttx.response.TtxBidExt;
import org.prebid.server.bidder.ttx.response.TtxBidExtTtx;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ttx.ExtImpTtx;

import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class TtxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private TtxBidder ttxBidder;

    @Before
    public void setUp() {
        ttxBidder = new TtxBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TtxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldAppendErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ttxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getMessage()
                .startsWith("Cannot deserialize instance")
                && error.getType() == BidderError.Type.bad_input);
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldSetSiteIdFromImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ttxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsOnly("siteId");
    }

    @Test
    public void makeHttpRequestsShouldGetDetailsOnlyFromFirstImpExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(identity()),
                        givenImp(impBuilder -> impBuilder
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTtx.of("11", "2", "3")))))
                ))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ttxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsOnly("siteId");
    }

    @Test
    public void makeHttpRequestsShouldChangeOnlyFirstImpExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(identity()),
                        givenImp(impBuilder -> impBuilder
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTtx.of("11", null, "3")))))
                ))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ttxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(
                        mapper.valueToTree(TtxImpExt.of(TtxImpExtTtx.of("productId", "zoneId"))),
                        mapper.valueToTree(ExtPrebid.of(null, ExtImpTtx.of("11", null, "3"))));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAnyOfVideoParamsNotPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .video(Video.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTtx.of("11", null, "3")))))
                ))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ttxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getMessage()
                .startsWith("One or more invalid or missing video field(s) w, h, protocols, mimes, playbackmethod")
                && error.getType() == BidderError.Type.bad_input);
    }

    @Test
    public void makeHttpRequestsShouldUpdateNotPresentPlacement() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .video(Video.builder()
                                        .w(23)
                                        .h(23)
                                        .mimes(singletonList("mime"))
                                        .protocols(singletonList(23))
                                        .playbackmethod(singletonList(27))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTtx.of("11", null, "3")))))
                ))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ttxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .containsExactly(Video.builder()
                        .placement(2)
                        .w(23)
                        .h(23)
                        .mimes(singletonList("mime"))
                        .protocols(singletonList(23))
                        .playbackmethod(singletonList(27))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldUpdatePlacementAndStartDelayIfProdIsInstream() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .video(Video.builder()
                                        .w(23)
                                        .h(23)
                                        .mimes(singletonList("mime"))
                                        .protocols(singletonList(23))
                                        .playbackmethod(singletonList(27))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTtx.of("11", null, "instream")))))
                ))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ttxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .containsExactly(Video.builder()
                        .placement(1)
                        .startdelay(0)
                        .w(23)
                        .h(23)
                        .mimes(singletonList("mime"))
                        .protocols(singletonList(23))
                        .playbackmethod(singletonList(27))
                        .build());
    }

    @Test
    public void makeBidsShouldReturnErrorIfNoBannerOrVideoPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .banner(null))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ttxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .allMatch(error -> error.getMessage()
                        .startsWith("At least one of [banner, video] formats must be defined in Imp. None found")
                        && error.getType() == BidderError.Type.bad_input);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = ttxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .allMatch(error -> error.getMessage().startsWith("Failed to decode: Unrecognized token")
                        && error.getType() == BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = ttxBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = ttxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = ttxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoInBidExt() throws JsonProcessingException {
        // given
        final TtxBidExt ttxBidExt = TtxBidExt.of(TtxBidExtTtx.of("video"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .ext(mapper.convertValue(ttxBidExt, ObjectNode.class)))));

        // when
        final Result<List<BidderBid>> result = ttxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder()
                        .ext(mapper.convertValue(ttxBidExt, ObjectNode.class))
                        .build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfExtNotContainVideoString() throws JsonProcessingException {
        // given
        final TtxBidExt ttxBidExt = TtxBidExt.of(TtxBidExtTtx.of("notVideo"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .ext(mapper.convertValue(ttxBidExt, ObjectNode.class)))));

        // when
        final Result<List<BidderBid>> result = ttxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder()
                        .ext(mapper.convertValue(ttxBidExt, ObjectNode.class))
                        .build(), banner, "USD"));
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
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTtx.of("siteId", "zoneId", "productId")))))
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
