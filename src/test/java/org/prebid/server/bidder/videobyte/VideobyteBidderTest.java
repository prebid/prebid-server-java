package org.prebid.server.bidder.videobyte;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.videobyte.ExtImpVideobyte;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class VideobyteBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://site.st/uri";

    private VideobyteBidder bidder;

    @Before
    public void setUp() {
        bidder = new VideobyteBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VideobyteBidder("invalid url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .allMatch(errorMessage -> errorMessage.startsWith("Ignoring imp id=null, error while decoding, err:"));
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestForEachImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(List::size)
                .containsOnly(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestsWithCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(ext -> ext.publisherId("1 23"), identity()),
                givenImp(ext -> ext.publisherId("456").placementId("a/bc"), identity()),
                givenImp(ext -> ext.publisherId("789").placementId("dce").networkId("A?a=BC"), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL + "?source=pbs&pid=" + HttpUtil.encodeUrl("1 23"),
                        ENDPOINT_URL + "?source=pbs&pid=456&placementId=" + HttpUtil.encodeUrl("a/bc"),
                        ENDPOINT_URL + "?source=pbs&pid=789&placementId=dce&nid=" + HttpUtil.encodeUrl("A?a=BC"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestsWithOriginHeaderIfSiteDomainIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Site.builder().domain("domain").build(),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.ORIGIN_HEADER.toString(), "domain"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestsWithRefererHeaderIfSiteRefIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Site.builder().ref("referer").build(),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "referer"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestsWithOriginAndRefererHeadersIfSiteDomainAndSiteRefArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Site.builder().domain("domain").ref("referer").build(),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.ORIGIN_HEADER.toString(), "domain"),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "referer"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("Incorrect body", null);

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).isEqualTo("Bad server response.");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("null", null);

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("{}", null);

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsWithCorrectMediaType() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse("id1", "id2"),
                givenBidRequest(givenImp(imp -> imp.id("id2")),
                        givenImp(imp -> imp.id("id1").banner(Banner.builder().build()))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).extracting(BidderBid::getType)
                .containsExactly(BidType.banner, BidType.video);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsWithDefaultCurrency() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("", ""),
                givenBidRequest(givenImp(identity()), givenImp(identity())));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getBidCurrency)
                .containsOnly("USD");
        assertThat(result.getErrors()).isEmpty();
    }

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()).build();
    }

    private BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(request -> request.imp(List.of(imps)));
    }

    private BidRequest givenBidRequest(Site site, Imp... imps) {
        return givenBidRequest(request -> request.site(site).imp(List.of(imps)));
    }

    private Imp givenImp(UnaryOperator<ExtImpVideobyte.ExtImpVideobyteBuilder> extCustomizer,
                         UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        final ExtImpVideobyte extImpVideobyte = extCustomizer.apply(ExtImpVideobyte.builder()).build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpVideobyte));
        return impCustomizer.apply(Imp.builder().ext(ext)).build();
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(identity(), impCustomizer);
    }

    private HttpCall<BidRequest> givenHttpCall(String body, BidRequest bidRequest) {
        final HttpRequest<BidRequest> request = HttpRequest.<BidRequest>builder().payload(bidRequest).build();
        final HttpResponse response = HttpResponse.of(200, null, body);
        return HttpCall.success(request, response, null);
    }

    private String givenBidResponse(String... impIds) throws JsonProcessingException {
        final List<SeatBid> seatBids = Arrays.stream(impIds)
                .map(impId -> Bid.builder().impid(impId).build())
                .map(Collections::singletonList)
                .map(bids -> SeatBid.builder().bid(bids).build())
                .collect(Collectors.toList());
        return mapper.writeValueAsString(BidResponse.builder().seatbid(seatBids).build());
    }
}
