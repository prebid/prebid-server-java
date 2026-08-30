package org.prebid.server.bidder.rtbstack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.rtbstack.ExtImpRtbStack;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;

public class RtbStackBidderTest extends VertxTest {

    private static final String ENDPOINT_URL =
            "https://{{Region}}-test.endpoint.com/pbs?ssp={{SspID}}&endpoint={{ZoneID}}&client={{PartnerId}}";
    private static final String ROUTE_US =
            "https://testsite.us-adx-admixer.test-route.com/prebid?client=client-1&endpoint=309&ssp=145";
    private static final String RESOLVED_US = "https://us-test.endpoint.com/pbs?ssp=145&endpoint=309&client=client-1";

    private final RtbStackBidder target = new RtbStackBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RtbStackBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldResolveEndpointFromRoute() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", ROUTE_US, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(RESOLVED_US);
    }

    @Test
    public void makeHttpRequestsShouldPromoteTagIdAndReplaceImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", ROUTE_US, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid, Imp::getExt)
                .containsExactly(tuple("tag1", mapper.createObjectNode()));
    }

    @Test
    public void makeHttpRequestsShouldKeepCustomParamsInImpExt() {
        // given
        final Map<String, Object> customParams = Map.of(
                "foo", "bar",
                "floor", 0.45,
                "enabled", true,
                "count", 3,
                "placement", Map.of("position", "top", "sticky", true),
                "sizes", List.of("970x250", "728x90"));
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", ROUTE_US, "tag1", customParams));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedExt = mapper.createObjectNode();
        expectedExt.set("customParams", mapper.valueToTree(customParams));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedExt);
    }

    @Test
    public void makeHttpRequestsShouldDropEmptyCustomParamsFromImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", ROUTE_US, "tag1", Map.of()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.createObjectNode());
    }

    @Test
    public void makeHttpRequestsShouldResolveSgRegionEndpoint() {
        // given
        final String routeSg = "https://mysite.sg-adx-admixer.test-route.com/prebid?client=c&endpoint=7&ssp=9";
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", routeSg, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://sg-test.endpoint.com/pbs?ssp=9&endpoint=7&client=c");
    }

    @Test
    public void makeHttpRequestsShouldEncodeRouteQueryParamValues() {
        // given
        final String route = "https://s.us-adx-admixer.test-route.com/prebid?client=client%201&endpoint=309&ssp=145";
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", route, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://us-test.endpoint.com/pbs?ssp=145&endpoint=309&client=client+1");
    }

    @Test
    public void makeHttpRequestsShouldGroupImpsByRouteInFirstSeenOrder() {
        // given
        final String routeEu = "https://site.eu-adx-admixer.test-route.com/prebid?client=abc&endpoint=1&ssp=2";
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", ROUTE_US, "tag1", null),
                givenImp("imp2", routeEu, "tag2", null),
                givenImp("imp3", ROUTE_US, "tag3", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getUri)
                .containsExactly(RESOLVED_US, "https://eu-test.endpoint.com/pbs?ssp=2&endpoint=1&client=abc");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getImp().stream().map(Imp::getId).toList())
                .containsExactly(List.of("imp1", "imp3"), List.of("imp2"));
    }

    @Test
    public void makeHttpRequestsShouldBatchSameRouteImpsIntoSingleRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", ROUTE_US, "tag1", null),
                givenImp("imp2", ROUTE_US, "tag2", null),
                givenImp("imp3", ROUTE_US, "tag3", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("imp1", "imp2", "imp3");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForImpWithInvalidExtAndKeepValidImp() {
        // given
        final Imp invalidImp = Imp.builder()
                .id("imp1")
                .ext((ObjectNode) mapper.createObjectNode().set("bidder", mapper.createArrayNode()))
                .build();
        final BidRequest bidRequest = givenBidRequest(invalidImp, givenImp("imp2", ROUTE_US, "tag2", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("Wrong RTBStack bidder ext"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("imp2");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForRouteWithInvalidRegion() {
        // given
        final String badRoute = "https://publisher.xx-adx-admixer.test-route.com/prebid?client=c&endpoint=1&ssp=2";
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", badRoute, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(badInput("unable to extract valid region from route URL hostname"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForRouteWithMissingQueryParams() {
        // given
        final String badRoute = "https://publisher.us-adx-admixer.test-route.com/prebid?client=c&endpoint=1";
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", badRoute, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(badInput("route URL must contain client, endpoint, and ssp query parameters"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForUnparseableRoute() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", "http://[bad-route", "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("invalid route URL:");
                });
    }

    @Test
    public void makeHttpRequestsShouldContinueOtherRoutesWhenOneRouteFails() {
        // given
        final String badRoute = "https://publisher.xx-adx-admixer.test-route.com/prebid?client=c&endpoint=1&ssp=2";
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", ROUTE_US, "tag1", null),
                givenImp("imp2", badRoute, "tag2", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(badInput("unable to extract valid region from route URL hostname"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(RESOLVED_US);
    }

    @Test
    public void makeHttpRequestsShouldSynthesizeSiteDomainFromPage() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("").page("https://example.com/path").build()),
                givenImp("imp1", ROUTE_US, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getDomain, Site::getPage)
                .containsExactly(tuple("example.com", "https://example.com/path"));
    }

    @Test
    public void makeHttpRequestsShouldFallBackToRawPageWhenPageHasNoHostname() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("").page("not-a-url").build()),
                givenImp("imp1", ROUTE_US, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getDomain)
                .containsExactly("not-a-url");
    }

    @Test
    public void makeHttpRequestsShouldFallBackToRawPageWhenPageIsUnparseable() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("").page("http://[bad-page").build()),
                givenImp("imp1", ROUTE_US, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getDomain)
                .containsExactly("http://[bad-page");
    }

    @Test
    public void makeHttpRequestsShouldKeepSiteUntouchedWhenDomainIsPresent() {
        // given
        final Site site = Site.builder().domain("example.com").page("https://other.com").build();
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(site),
                givenImp("imp1", ROUTE_US, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsExactly(site);
    }

    @Test
    public void makeHttpRequestsShouldTolerateAbsentSite() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", ROUTE_US, "tag1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid_json");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid_json'");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(mapper.writeValueAsString(BidResponse.builder().seatbid(null).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(1);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(2);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(videoBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnAudioBid() throws JsonProcessingException {
        // given
        final Bid audioBid = givenBid(3);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(audioBid, BidType.audio, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(4);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(nativeBid, BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldPassResponseCurrencyThrough() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(1);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(null, bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(bannerBid, BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedMtype() throws JsonProcessingException {
        // given
        final Bid bidWithUnsupportedMtype = givenBid(0);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", bidWithUnsupportedMtype));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(badServerResponse("unsupported MType 0 for bid impId"));
    }

    @Test
    public void makeBidsShouldReturnErrorForMissingMtype() throws JsonProcessingException {
        // given
        final Bid bidWithoutMtype = givenBid(null);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", bidWithoutMtype));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(badServerResponse("unsupported MType null for bid impId"));
    }

    @Test
    public void makeBidsShouldSkipInvalidBidAndKeepValidOne() throws JsonProcessingException {
        // given
        final Bid validBid = givenBid(1);
        final Bid invalidBid = givenBid(0);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse("USD", validBid, invalidBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(badServerResponse("unsupported MType 0 for bid impId"));
        assertThat(result.getValue()).containsOnly(BidderBid.of(validBid, BidType.banner, "USD"));
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(request -> request, imps);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              Imp... imps) {

        return requestCustomizer.apply(BidRequest.builder().imp(List.of(imps))).build();
    }

    private static Imp givenImp(String impId, String route, String tagId, Map<String, Object> customParams) {
        return Imp.builder()
                .id(impId)
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRtbStack.of(route, tagId, customParams))))
                .build();
    }

    private static Bid givenBid(Integer mtype) {
        return Bid.builder().id("bidId").impid("impId").price(BigDecimal.ONE).mtype(mtype).build();
    }

    private static String givenBidResponse(String currency, Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur(currency)
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
