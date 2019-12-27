package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.pubmatic.proto.PubmaticRequestExt;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmaticKeyVal;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class PubmaticBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.endpoint.com/translator?source=prebid-server";

    private PubmaticBidder pubmaticBidder;

    @Before
    public void setUp() {
        pubmaticBidder = new PubmaticBidder(ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PubmaticBidder("invalid_url"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoBannerOrVideo() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "Invalid MediaType. PubMatic only supports Banner and Video. Ignoring ImpID=123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("invalid ad slot@"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid adSlot provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidSizes() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@300x200x100"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid size provided in adSlot"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidWidth() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@widthx200"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid size provided in adSlot"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidHeight() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@300xHeight:1"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid size provided in adSlot"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfKeywordsAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .keywords(singletonList(ExtImpPubmaticKeyVal.of("\"", singletonList("\"")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to create keywords with error: " +
                "Unexpected character");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfWrapExtHasInvalidParams() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .wrapper(mapper.valueToTree(singletonMap("key", "invalid value"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Error in Wrapper Parameters");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetAudioToNullIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.audio(Audio.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getAudio).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetBannerWidthAndHeightFromAdSlot() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getH).containsOnly(250);
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW).containsOnly(300);
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtNullIfKeywordsAreNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtNullIfKeywordsAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.keywords(emptyList()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtFromKeywords() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .keywords(singletonList(
                                ExtImpPubmaticKeyVal.of("key2", asList("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnly(mapper.readValue("{\"key2\":\"value1,value2\"}", ObjectNode.class));
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtFromKeywordsSkippingKeysWithEmptyValues() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .keywords(asList(
                                ExtImpPubmaticKeyVal.of("key with empty value", emptyList()),
                                ExtImpPubmaticKeyVal.of("key2", asList("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnly(mapper.readValue("{\"key2\":\"value1,value2\"}", ObjectNode.class));
    }

    @Test
    public void makeHttpRequestsShouldSetRequestExtFromWrapExt() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .wrapper(mapper.valueToTree(singletonMap("key", 1))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt)
                .containsOnly(mapper.valueToTree(
                        PubmaticRequestExt.of(mapper.valueToTree(singletonMap("key", 1)))));
    }

    @Test
    public void makeHttpRequestsShouldNotChangeExtIfWrapExtIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(mapper.createObjectNode()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt)
                .containsOnly(mapper.createObjectNode());
    }

    @Test
    public void makeHttpRequestsShouldSetSitePublisherIdFromImpExtPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldNotSetAppPublisherIdIfSiteIsNotNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().build())
                        .app(App.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getId)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetAppPublisherIdIfSiteIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.app(App.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0)
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns("http://test.endpoint.com/translator?source=prebid-server", HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfExtBidContainsBidTypeOne() throws JsonProcessingException {
        // given
        final ObjectNode bidType = mapper.createObjectNode().put("BidType", 1);
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").ext(bidType))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(bidType).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnXNativeBidIfExtBidContainsBidTypeTwo() throws JsonProcessingException {
        // given
        final ObjectNode bidType = mapper.createObjectNode().put("BidType", 2);
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").ext(bidType))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(bidType).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfExtBidContainsIllegalBidType() throws JsonProcessingException {
        // given
        final ObjectNode bidType = mapper.createObjectNode().put("BidType", 100);
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").ext(bidType))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(bidType).build(), banner, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(pubmaticBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpPubmatic.ExtImpPubmaticBuilder, ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpPubmatic.ExtImpPubmaticBuilder, ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {

        return givenBidRequest(identity(), impCustomizer, extCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                Function<ExtImpPubmatic.ExtImpPubmaticBuilder, ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        extCustomizer.apply(ExtImpPubmatic.builder()
                                .publisherId("pub id")
                                .adSlot("slot@300x250"))
                                .build()))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
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
