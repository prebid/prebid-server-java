package org.prebid.server.bidder.matterfullrtb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.matterfull.MatterfullrtbBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.matterfull.ExtImpMatterfullrtb;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;

@ExtendWith(MockitoExtension.class)
public class MatterfullrtbBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.endpont.com?pid={PublisherID}";

    private MatterfullrtbBidder target;

    @BeforeEach
    public void setUp() {
        target = new MatterfullrtbBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new MatterfullrtbBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestShouldReturnErrorsWithoutRequestWhenAllImpsAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("invalid imp.ext for impression");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnEmptyResultWhenImpsAreEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(Collections.emptyList()).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldModifyAppAndSite() {
        // given
        final Site site = Site.builder()
                .publisher(Publisher.builder()
                        .id("origPub")
                        .build())
                .build();
        final App app = App.builder()
                .domain("origDomain")
                .publisher(Publisher.builder()
                        .id("origPub")
                        .build())
                .build();
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(site).app(app),
                givenImp(imp -> imp.ext(givenImpExt(ExtImpMatterfullrtb.of("pub")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp, BidRequest::getSite)
                .containsOnly(tuple(
                        App.builder().domain("").publisher(null).build(),
                        Site.builder().publisher(null).build()));
    }

    @Test
    public void makeHttpRequestShouldNotModifyWhenAppAndSiteAreNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(null).app(null),
                givenImp(imp -> imp.ext(givenImpExt(ExtImpMatterfullrtb.of("pub")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .containsOnlyNulls();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestShouldResolveUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.ext(givenImpExt(ExtImpMatterfullrtb.of("pub")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("http://test.endpont.com?pid=pub");
    }

    @Test
    public void makeHttpRequestShouldCollectImpsWithSamePublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.id("imp-1").ext(givenImpExt(ExtImpMatterfullrtb.of("pub")))),
                givenImp(imp -> imp.id("imp-2").ext(givenImpExt(ExtImpMatterfullrtb.of("pub")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(List::size)
                .containsOnly(2);
    }

    @Test
    public void makeHttpRequestShouldCreateTwoRequestForDifferentPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.id("imp-1").ext(givenImpExt(ExtImpMatterfullrtb.of("pub-1")))),
                givenImp(imp -> imp.id("imp-2").ext(givenImpExt(ExtImpMatterfullrtb.of("pub-2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid_json");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid_json'");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(2);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(videoBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(1);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(videoBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorsForUnsupportedMtypes() throws JsonProcessingException {
        // given
        final Bid validBid = givenBid(1);
        final Bid invalidBid = givenBid(5);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(validBid, invalidBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("Unsupported mtype 5 for impression with ID: impId"));
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(validBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidShouldReturnMediaTypeFromImpWhenBidMTypeEqualsZero() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(0, "imp-2");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.id("imp-2").banner(Banner.builder().build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(bid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidShouldReturnErrorWhenBidMTypeEqualsZeroAndImpInvalid() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(0, "imp-2");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.id("imp-2").banner(null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("Could not determine media type for impression with ID: imp-2"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidShouldReturnErrorWhenImpIsMultiformatAndMTypeEqualsZero() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(0, "imp-2");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.id("imp-2")
                        .video(Video.builder().build())
                        .banner(Banner.builder().build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(
                        badServerResponse("Bid must have non-zero mtype for multi-format impression with ID: imp-2"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidShouldReturnErrorWhenBidMTypeEqualsZeroAndImpNotFound() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(0, "imp-1");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.id("imp-2").banner(Banner.builder().build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("Failed to find impression for ID: imp-1"));
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {
        return bidRequestCustomizer.apply(BidRequest.builder().imp(List.of(imps))).build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder().imp(singletonList(givenImp(impCustomizer))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(givenImpExt(ExtImpMatterfullrtb.of("publisherId"))))
                .build();
    }

    private static ObjectNode givenImpExt(ExtImpMatterfullrtb extImpMatterfullrtb) {
        return mapper.valueToTree(ExtPrebid.of(null, extImpMatterfullrtb));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static Bid givenBid(Integer mtype) {
        return givenBid(mtype, "impId");
    }

    private static Bid givenBid(Integer mtype, String impId) {
        return Bid.builder().id("bidId").impid(impId).price(BigDecimal.ONE).mtype(mtype).build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }
}
