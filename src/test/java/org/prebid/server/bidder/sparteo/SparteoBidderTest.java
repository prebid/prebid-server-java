package org.prebid.server.bidder.sparteo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class SparteoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL =
            "https://test.sparteo.com/endpoint";
    private final SparteoBidder sparteoBidder = new SparteoBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SparteoBidder("invalid_url", jacksonMapper))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void makeHttpRequestsShouldSetNetworkIdOnSitePublisherExtWhenPresentInImp() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode()
                .put("networkId", "testNetworkId")
                .put("customParam", "customValue"));

        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().publisher(Publisher.builder().build()).build()),
                givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingSiteDomainWarning(result);
        assertThat(result.getValue())
                .extracting(HttpRequest::getMethod, HttpRequest::getUri)
                .containsExactly(tuple(HttpMethod.POST,
                        "https://test.sparteo.com/endpoint?network_id=testNetworkId&site_domain=unknown"));

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .allSatisfy(ext -> {
                    assertThat(ext.at("/sparteo/params/networkId").asText()).isEqualTo("testNetworkId");
                    assertThat(ext.at("/sparteo/params/customParam").asText()).isEqualTo("customValue");
                    assertThat(ext.has("bidder")).isFalse();
                });

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getExt)
                .extracting(ext -> ext.getProperties().get("params").get("networkId").asText())
                .containsExactly("testNetworkId");
    }

    @Test
    public void makeHttpRequestsShouldUseFirstNetworkIdWhenMultipleImpsDefineIt() {
        // given
        final ObjectNode impExt1 = mapper.createObjectNode();
        impExt1.set("bidder", mapper.createObjectNode().put("networkId", "id1"));
        final ObjectNode impExt2 = mapper.createObjectNode();
        impExt2.set("bidder", mapper.createObjectNode().put("networkId", "id2"));

        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().publisher(Publisher.builder().build()).build()),
                givenImp(imp -> imp.id("imp1").ext(impExt1)),
                givenImp(imp -> imp.id("imp2").ext(impExt2)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingSiteDomainWarning(result);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getExt)
                .extracting(ext -> ext.getProperties().get("params").get("networkId").asText())
                .containsExactly("id1");
    }

    @Test
    public void makeHttpRequestsShouldMergeSparteoParamsWithBidderParamsOnConflict() {
        // given
        final ObjectNode bidderNode = mapper.createObjectNode();
        bidderNode.put("conflictingParam", "bidderValue");
        bidderNode.put("networkId", "id1");

        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", bidderNode);
        final ObjectNode sparteoNode = impExt.putObject("sparteo");
        sparteoNode.putObject("params").put("conflictingParam", "sparteoValue");

        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting((JsonNode ext) -> ext.at("/sparteo/params/conflictingParam").asText())
                .containsExactly("bidderValue");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting((JsonNode ext) -> ext.at("/sparteo/params/networkId").asText())
                .containsExactly("id1");
    }

    @Test
    public void makeHttpRequestsShouldHandleRequestWithoutSiteOrPublisher() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "testNetworkId"));

        final BidRequest bidRequestNoSite = givenBidRequest(
                request -> request.site(null),
                givenImp(imp -> imp.ext(impExt)));

        final BidRequest bidRequestNoPublisher = givenBidRequest(
                request -> request.site(Site.builder().publisher(null).build()),
                givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> resultNoSite = sparteoBidder.makeHttpRequests(bidRequestNoSite);
        final Result<List<HttpRequest<BidRequest>>> resultNoPublisher =
                sparteoBidder.makeHttpRequests(bidRequestNoPublisher);

        // then
        assertThat(resultNoSite.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsNull();

        assertThat(resultNoSite.getErrors()).isEmpty();
        assertThat(resultNoPublisher.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getExt)
                .extracting(ext -> ext.getProperties().get("params").get("networkId").asText())
                .containsExactly("testNetworkId");
    }

    @Test
    public void makeHttpRequestsShouldMergeNetworkIdIntoExistingPublisherExtParams() throws JsonProcessingException {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "testNetworkId"));

        final ObjectNode publisherExtNode = mapper.createObjectNode();
        publisherExtNode.putObject("params").put("existingParam", "existingValue");
        final ExtPublisher extPublisher = mapper.convertValue(publisherExtNode, ExtPublisher.class);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().publisher(
                        Publisher.builder().ext(extPublisher).build()).build()),
                givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingSiteDomainWarning(result);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getExt)
                .extracting(ext -> ext.getProperties().get("params"))
                .allSatisfy(params -> {
                    assertThat(params.get("networkId").asText()).isEqualTo("testNetworkId");
                    assertThat(params.get("existingParam").asText()).isEqualTo("existingValue");
                });
    }

    @Test
    public void makeHttpRequestsShouldAddParamsToPublisherExtWhenExtExistsWithoutParams()
            throws JsonProcessingException {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "testNetworkId"));

        final ObjectNode publisherExtJson = mapper.createObjectNode();
        publisherExtJson.put("otherField", "otherValue");
        final ExtPublisher extPublisher = mapper.convertValue(publisherExtJson, ExtPublisher.class);

        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().publisher(
                        Publisher.builder().ext(extPublisher).build()).build()),
                givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingSiteDomainWarning(result);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getExt)
                .extracting(ext -> ext.getProperties())
                .allSatisfy(properties -> {
                    assertThat(properties.get("params").get("networkId").asText()).isEqualTo("testNetworkId");
                    assertThat(properties.get("otherField").asText()).isEqualTo("otherValue");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnEmptyResultWhenRequestHasNoImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(Collections.emptyList()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldAppendSiteDomainAndNetworkIdAsQueryParams() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "testNetworkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r.site(Site.builder()
                                .domain("dev.sparteo.com")
                                .publisher(Publisher.builder().build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=testNetworkId&site_domain=dev.sparteo.com");
    }

    @Test
    public void makeHttpRequestsShouldAppendSitePageDomainAndNetworkIdAsQueryParams() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "testNetworkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r.site(Site.builder()
                                .domain(null)
                                .page("https://www.dev.sparteo.com:3000/p")
                                .publisher(Publisher.builder().build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=testNetworkId&site_domain=dev.sparteo.com");
    }

    @Test
    public void makeHttpRequestsShouldUseSiteDomainWhenPublisherDomainIsMissing() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "testNetworkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r.site(Site.builder()
                                .domain("dev.sparteo.com")
                                .publisher(Publisher.builder().build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=testNetworkId&site_domain=dev.sparteo.com");
    }

    @Test
    public void makeHttpRequestsShouldPreferSiteDomainOverPublisherDomain() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "networkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r.site(Site.builder()
                                .domain("site.sparteo.com")
                                .publisher(Publisher.builder().domain("dev.sparteo.com").build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=networkId&site_domain=site.sparteo.com");
    }

    @Test
    public void makeHttpRequestsShouldUseAppDomainWhenNoSite() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "networkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r
                        .site(null)
                        .app(App.builder()
                                .domain("com.sparteo.app")
                                .publisher(Publisher.builder().id("p1").build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingBundleWarning(result);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=networkId&app_domain=com.sparteo.app&bundle=unknown");
    }

    @Test
    public void makeHttpRequestsShouldSetNetworkIdOnAppPublisherExtWhenNoSite() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "networkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r
                        .site(null)
                        .app(App.builder()
                                .domain("com.sparteo.app")
                                .publisher(Publisher.builder().build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingBundleWarning(result);
        final BidRequest out = result.getValue().get(0).getPayload();
        assertThat(out.getApp()).isNotNull();
        assertThat(out.getApp().getPublisher()).isNotNull();

        final ExtPublisher ext = out.getApp().getPublisher().getExt();
        assertThat(ext).isNotNull();
        assertThat(ext.getProperties()).containsKey("params");
        assertThat(ext.getProperties().get("params").get("networkId").asText()).isEqualTo("networkId");
    }

    @Test
    public void makeHttpRequestsShouldCreateAppPublisherWhenMissingAndSetNetworkId() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "networkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r
                        .site(null)
                        .app(App.builder()
                                .domain("com.sparteo.app")
                                .publisher(null)
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingBundleWarning(result);
        final BidRequest out = result.getValue().get(0).getPayload();
        assertThat(out.getApp()).isNotNull();
        assertThat(out.getApp().getPublisher()).isNotNull();

        final ExtPublisher ext = out.getApp().getPublisher().getExt();
        assertThat(ext).isNotNull();
        assertThat(ext.getProperties()).containsKey("params");
        assertThat(ext.getProperties().get("params").get("networkId").asText()).isEqualTo("networkId");
    }

    @Test
    public void makeHttpRequestsShouldAppendBundleWhenAppBundlePresent() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "networkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r
                        .site(null)
                        .app(App.builder()
                                .domain("dev.sparteo.com")
                                .bundle("com.sparteo.app")
                                .publisher(Publisher.builder().id("p1").build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=networkId&app_domain=dev.sparteo.com&bundle=com.sparteo.app");
    }

    @Test
    public void makeHttpRequestsShouldNotAppendBundleWhenNoAppBundle() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "networkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r
                        .site(Site.builder().domain("dev.sparteo.com").publisher(Publisher.builder().build()).build())
                        .app(null),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=networkId&site_domain=dev.sparteo.com");
    }

    @Test
    public void makeHttpRequestsShouldAppendUnknownBundleWhenAppPresentButBundleMissing() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "networkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r
                        .site(null)
                        .app(App.builder()
                                .domain("dev.sparteo.com")
                                .publisher(Publisher.builder().id("p1").build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingBundleWarning(result);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=networkId&app_domain=dev.sparteo.com&bundle=unknown");
    }

    @Test
    public void makeHttpRequestsShouldAppendUnknownBundleWhenAppBundleIsNull() {
        testAppendUnknownBundle(null);
    }

    @Test
    public void makeHttpRequestsShouldAppendUnknownBundleWhenAppBundleIsEmpty() {
        testAppendUnknownBundle("");
    }

    @Test
    public void makeHttpRequestsShouldAppendUnknownBundleWhenAppBundleIsBlank() {
        testAppendUnknownBundle("   ");
    }

    @Test
    public void makeHttpRequestsShouldAppendUnknownBundleWhenAppBundleIsLiteralNull() {
        testAppendUnknownBundle("null");
    }

    @Test
    public void makeHttpRequestsShouldAppendUnknownBundleWhenAppBundleIsLiteralNullIgnoreCase() {
        testAppendUnknownBundle("NuLl");
    }

    private void testAppendUnknownBundle(String rawBundle) {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "networkId"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r
                        .site(null)
                        .app(App.builder()
                                .domain("dev.sparteo.com")
                                .bundle(rawBundle)
                                .publisher(Publisher.builder().id("p1").build())
                                .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertMissingBundleWarning(result);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.sparteo.com/endpoint?network_id=networkId&app_domain=dev.sparteo.com&bundle=unknown");
    }

    @Test
    public void makeHttpRequestsShouldWarnAndSetUnknownAppDomainWhenAppDomainMissing() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.createObjectNode().put("networkId", "nid"));

        final BidRequest bidRequest = givenBidRequest(
                r -> r.site(null)
                      .app(App.builder()
                              .domain(null)
                              .bundle("com.example.bundle")
                              .publisher(Publisher.builder().build())
                              .build()),
                givenImp(i -> i.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        "https://test.sparteo.com/endpoint?network_id=nid&app_domain=unknown&bundle=com.example.bundle");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseStatusIs204() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(givenBidRequest()).build(),
                HttpResponse.of(204, null, ""),
                null);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage())
                            .startsWith("Failed to decode: No content to map due to end-of-input");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseStatusIsNot200Or204() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(givenBidRequest()).build(),
                HttpResponse.of(400, null, "Bad Request"),
                null);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'Bad'");
                });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyIsInvalidJson() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(givenBidRequest()).build(),
                HttpResponse.of(200, null, "invalid_json"),
                null);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid_json'");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(givenBidRequest()).build(),
                HttpResponse.of(400, null, "null"),
                null);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenBidResponseHasNoSeatBids() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = BidResponse.builder().seatbid(Collections.emptyList()).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMediaTypeIsBanner() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(builder -> builder.impid("imp1").price(BigDecimal.valueOf(1.23)).adm("adm-banner"),
                BidType.banner.getName());
        final BidResponse bidResponse = givenBidResponse(bid, "EUR");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(bid.toBuilder().mtype(1).build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenMediaTypeIsVideo() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(builder ->
                        builder.impid("imp2").price(BigDecimal.valueOf(2.34)).adm("adm-video"),
                BidType.video.getName());
        final BidResponse bidResponse = givenBidResponse(bid, "EUR");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(bid.toBuilder().mtype(2).build(), BidType.video, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenMediaTypeIsNative() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(builder ->
                        builder.impid("imp3").price(BigDecimal.valueOf(3.45)).adm("adm-native"),
                BidType.xNative.getName());
        final BidResponse bidResponse = givenBidResponse(bid, "EUR");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(bid.toBuilder().mtype(4).build(), BidType.xNative, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedMediaTypeAndProcessOthers() throws JsonProcessingException {
        // given
        final Bid audioBid = givenBid(builder ->
                builder.impid("impAudio").price(BigDecimal.ONE),
                BidType.audio.getName());
        final Bid bannerBid = givenBid(builder ->
                builder.impid("impBanner").price(BigDecimal.valueOf(2.0)),
                BidType.banner.getName());
        final BidResponse bidResponse = givenBidResponse(List.of(audioBid, bannerBid), "EUR");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Audio bid type not supported by this adapter for impression id: impAudio");

        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsExactly(bannerBid.toBuilder().mtype(1).build());
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtIsNull() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(builder -> builder.impid("imp1").ext(null), null);
        final BidResponse bidResponse = givenBidResponse(bid, "USD");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mediatype for impression \"imp1\"");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenPrebidIsMissingInBidExt() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(builder -> builder.impid("imp1").ext(mapper.createObjectNode()), null);
        final BidResponse bidResponse = givenBidResponse(bid, "USD");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mediatype for impression \"imp1\"");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenPrebidTypeIsMissingInBidExt() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(builder -> builder.impid("imp1").ext(createBidExtWithEmptyPrebid()), null);
        final BidResponse bidResponse = givenBidResponse(bid, "USD");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mediatype for impression \"imp1\"");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenPrebidCannotBeParsed() throws JsonProcessingException {
        // given
        final ObjectNode malformedExt = mapper.createObjectNode();
        malformedExt.putArray("prebid");
        final Bid bid = givenBid(builder -> builder.impid("imp1").ext(malformedExt), null);
        final BidResponse bidResponse = givenBidResponse(bid, "USD");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mediatype for impression \"imp1\"");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenPrebidTypeIsUnsupported() throws JsonProcessingException {
        // given
        final Bid bid = givenBid(builder -> builder.impid("imp1"), "unknown-type");
        final BidResponse bidResponse = givenBidResponse(bid, "USD");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mediatype for impression \"imp1\"");
    }

    @Test
    public void makeBidsShouldProcessValidBidsWhenSeatBidContainsNulls() throws JsonProcessingException {
        // given
        final Bid validBid = givenBid(builder ->
                        builder.impid("validImp").price(BigDecimal.ONE),
                BidType.banner.getName());
        final List<Bid> bids = new ArrayList<>();
        bids.add(null);
        bids.add(validBid);

        final BidResponse bidResponse = givenBidResponse(bids, "USD");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsExactly(validBid.toBuilder().mtype(1).build());
    }

    @Test
    public void makeBidsShouldCorrectlyProcessMultipleBidsAndSeatBids() throws JsonProcessingException {
        // given
        final Bid bid1 = givenBid(builder ->
                        builder.impid("imp1").price(BigDecimal.valueOf(1.0)),
                BidType.banner.getName());
        final Bid bid2 = givenBid(builder ->
                        builder.impid("imp2").price(BigDecimal.valueOf(2.0)),
                BidType.video.getName());
        final Bid bid3 = givenBid(builder ->
                        builder.impid("imp3").price(BigDecimal.valueOf(3.0)),
                BidType.xNative.getName());

        final SeatBid seatBid1 = SeatBid.builder().bid(asList(bid1, bid2)).build();
        final SeatBid seatBid2 = SeatBid.builder().bid(singletonList(bid3)).build();

        final BidResponse bidResponse = BidResponse.builder().cur("USD").seatbid(asList(seatBid1, seatBid2)).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .hasSize(3)
                .extracting((BidderBid bidderBid) -> bidderBid.getBid().getImpid(), BidderBid::getType)
                .containsExactlyInAnyOrder(
                        tuple("imp1", BidType.banner),
                        tuple("imp2", BidType.video),
                        tuple("imp3", BidType.xNative));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenPrebidExtIsNullNode() throws JsonProcessingException {
        // given
        final ObjectNode bidExtWithNullPrebid = mapper.createObjectNode();
        bidExtWithNullPrebid.set("prebid", NullNode.getInstance());

        final Bid bid = givenBid(builder -> builder.impid("imp1").ext(bidExtWithNullPrebid), null);
        final BidResponse bidResponse = givenBidResponse(bid, "USD");
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mediatype for impression \"imp1\"");
    }

    @Test
    public void makeBidsShouldProcessValidSeatBidsWhenResponseContainsNulls() throws JsonProcessingException {
        // given
        final Bid validBid1 = givenBid(builder ->
                        builder.impid("validImp1").price(BigDecimal.TEN),
                BidType.banner.getName());
        final Bid validBid2 = givenBid(builder ->
                        builder.impid("validImp2").price(BigDecimal.ONE),
                BidType.banner.getName());

        final SeatBid validSeatBid1 = SeatBid.builder().bid(singletonList(validBid1)).build();
        final SeatBid validSeatBid2 = SeatBid.builder().bid(singletonList(validBid2)).build();

        final List<SeatBid> seatBidsWithNull = new ArrayList<>();
        seatBidsWithNull.add(validSeatBid1);
        seatBidsWithNull.add(null);
        seatBidsWithNull.add(validSeatBid2);

        final BidResponse bidResponse = BidResponse.builder().cur("USD").seatbid(seatBidsWithNull).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getImpid)
                .containsExactlyInAnyOrder("validImp1", "validImp2");
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenSeatBidHasNullBidList() throws JsonProcessingException {
        // given
        final SeatBid seatBidWithNullBids = SeatBid.builder().bid(null).build();
        final BidResponse bidResponse =
                BidResponse.builder().cur("USD").seatbid(singletonList(seatBidWithNullBids)).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenSeatBidHasEmptyBidList() throws JsonProcessingException {
        // given
        final SeatBid seatBidWithEmptyBids = SeatBid.builder().bid(Collections.emptyList()).build();
        final BidResponse bidResponse =
                BidResponse.builder().cur("USD").seatbid(singletonList(seatBidWithEmptyBids)).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), bidResponse);

        // when
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> customizer, Imp... imps) {
        return customizer.apply(BidRequest.builder().imp(asList(imps))).build();
    }

    private BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("impId")).build();
    }

    private Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer, String mediaType) {
        final Bid.BidBuilder builder = Bid.builder();
        bidCustomizer.apply(builder);
        if (builder.build().getExt() == null && mediaType != null) {
            builder.ext(createBidExtWithType(mediaType));
        }
        return builder.build();
    }

    private BidResponse givenBidResponse(List<Bid> bids, String currency) {
        return BidResponse.builder()
                .cur(currency)
                .seatbid(singletonList(SeatBid.builder().bid(bids).build()))
                .build();
    }

    private BidResponse givenBidResponse(Bid bid, String currency) {
        return givenBidResponse(singletonList(bid), currency);
    }

    private BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, BidResponse bidResponse) {
        try {
            final String body = mapper.writeValueAsString(bidResponse);
            return BidderCall.succeededHttp(
                    HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                    HttpResponse.of(200, null, body),
                    null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize BidResponse in test setup", e);
        }
    }

    private ObjectNode createBidExtWithType(String bidType) {
        final ObjectNode bidExt = mapper.createObjectNode();
        final ObjectNode prebidNode = mapper.createObjectNode();
        prebidNode.put("type", bidType);
        bidExt.set("prebid", prebidNode);
        return bidExt;
    }

    private ObjectNode createBidExtWithEmptyPrebid() {
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.set("prebid", mapper.createObjectNode());
        return bidExt;
    }

    private static void assertMissingSiteDomainWarning(Result<?> result) {
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(err -> {
                    assertThat(err.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(err.getMessage())
                            .contains("Domain not found. Missing the site.domain or the site.page field");
                });
    }

    private static void assertMissingBundleWarning(Result<?> result) {
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(err -> {
                    assertThat(err.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(err.getMessage())
                            .contains("Bundle not found. Missing the app.bundle field.");
                });
    }
}
