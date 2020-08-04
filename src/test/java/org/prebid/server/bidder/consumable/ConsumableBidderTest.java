package org.prebid.server.bidder.consumable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.consumable.model.ConsumableBidGdpr;
import org.prebid.server.bidder.consumable.model.ConsumableBidRequest;
import org.prebid.server.bidder.consumable.model.ConsumableBidResponse;
import org.prebid.server.bidder.consumable.model.ConsumableContents;
import org.prebid.server.bidder.consumable.model.ConsumableDecision;
import org.prebid.server.bidder.consumable.model.ConsumablePlacement;
import org.prebid.server.bidder.consumable.model.ConsumablePricing;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.consumable.ExtImpConsumable;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class ConsumableBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private ConsumableBidder consumableBidder;

    @Before
    public void setUp() {
        consumableBidder = new ConsumableBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConsumableBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetDefaultHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));

    }

    @Test
    public void makeHttpRequestsShouldSetAdditionalHeadersIfDeviceIsNotNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .device(Device.builder()
                                .ip("123.123.123.321")
                                .ua("some_ua")
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "some_ua"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "123.123.123.321"),
                        tuple("Forwarded", "for=123.123.123.321"));
    }

    @Test
    public void makeHttpRequestsShouldSetAdditionalHeadersIfUserBuyerUidIsNotBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .user(User.builder().buyeruid("buyer_id").build()),
                identity());

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpUtil.COOKIE_HEADER.toString(), "azk=buyer_id"));
    }

    @Test
    public void makeHttpRequestsShouldSetAdditionalHeadersIfSitePageIsNotBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .site(Site.builder().page("http://test.com").build()),
                identity());

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.REFERER_HEADER.toString(), "http://test.com"),
                        tuple(HttpUtil.ORIGIN_HEADER.toString(), "http://test.com"));
    }

    @Test
    public void makeHttpRequestsShouldSetRequestDefaultFields() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), ConsumableBidRequest.class))
                .extracting(ConsumableBidRequest::getIncludePricingData,
                        ConsumableBidRequest::getEnableBotFiltering, ConsumableBidRequest::getParallel)
                .containsOnly(tuple(true, true, true));
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), ConsumableBidRequest.class))
                .extracting(ConsumableBidRequest::getTime)
                .isNotNull();
    }

    @Test
    public void makeHttpRequestsShouldSetRequestFieldsFromFirstImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequestWithTwoImpsAndTwoFormats();

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), ConsumableBidRequest.class))
                .extracting(ConsumableBidRequest::getNetworkId, ConsumableBidRequest::getSiteId,
                        ConsumableBidRequest::getUnitId, ConsumableBidRequest::getUnitName)
                .containsOnly(tuple(111, 222, 333, "unit_name"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithExpectedPlacements() {
        // given
        final BidRequest bidRequest = givenBidRequestWithTwoImpsAndTwoFormats();

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), ConsumableBidRequest.class))
                .flatExtracting(ConsumableBidRequest::getPlacements)
                .containsOnly(
                        ConsumablePlacement.builder().divName("firstImp").networkId(111).siteId(222)
                                .unitId(333).unitName("unit_name").adTypes(singletonList(1)).build(),
                        ConsumablePlacement.builder().divName("second_imp").networkId(123).siteId(234)
                                .unitId(345).unitName("unit").adTypes(Arrays.asList(3, 429)).build());
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectGdprParameters() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<ConsumableBidRequest>>> result = consumableBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), ConsumableBidRequest.class))
                .flatExtracting(ConsumableBidRequest::getGdpr)
                .containsOnly(ConsumableBidGdpr.builder().applies(true).consent("consent").build());
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<ConsumableBidRequest> httpCall = HttpCall.success(null,
                HttpResponse.of(200, null, "invalid"), null);

        // when
        final Result<List<BidderBid>> result = consumableBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipDecisionsWithNullPricing() throws JsonProcessingException {
        // given
        final HttpCall<ConsumableBidRequest> httpCall = givenHttpCall(identity(),
                decision -> decision.pricing(null));

        // when
        final Result<List<BidderBid>> result = consumableBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipDecisionsWithNullClearPrice() throws JsonProcessingException {
        // given
        final HttpCall<ConsumableBidRequest> httpCall = givenHttpCall(identity(),
                decision -> decision.pricing(ConsumablePricing.of(null)));

        // when
        final Result<List<BidderBid>> result = consumableBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipDecisionsWithAbsentImpIdAndAddError() throws JsonProcessingException {
        // given
        final Map<String, ConsumableDecision> decisionMap = new HashMap<>();
        decisionMap.put("firstImp", ConsumableDecision.builder().pricing(ConsumablePricing.of(10.5)).build());
        decisionMap.put("missing_Imp", ConsumableDecision.builder().pricing(ConsumablePricing.of(1.1)).build());

        final HttpCall<ConsumableBidRequest> httpCall = givenHttpCall(() -> ConsumableBidResponse.of(decisionMap));

        // when
        final Result<List<BidderBid>> result = consumableBidder.makeBids(httpCall,
                givenBidRequestWithTwoImpsAndTwoFormats());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("ignoring bid id=request_id, request doesn't contain any "
                        + "impression with id=missing_Imp"));
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldReturnBannerBidWithExpectedFields() throws JsonProcessingException {
        // given
        final HttpCall<ConsumableBidRequest> httpCall = givenHttpCall(identity(),
                decision -> decision.pricing(ConsumablePricing.of(11.1)).adId(123L)
                        .contents(singletonList(ConsumableContents.of("contents_body"))));

        // when
        final Result<List<BidderBid>> result = consumableBidder.makeBids(httpCall,
                givenBidRequestWithTwoImpsAndTwoFormats());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .contains(BidderBid.of(
                        Bid.builder()
                                .id("request_id").impid("firstImp").price(BigDecimal.valueOf(11.1))
                                .adm("contents_body").w(120).h(90).exp(30).crid("123").build(),
                        BidType.banner, null));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(consumableBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequestWithTwoImpsAndTwoFormats() {
        return BidRequest.builder()
                .id("request_id")
                .imp(Arrays.asList(givenImp(identity()),
                        Imp.builder()
                                .id("second_imp")
                                .banner(Banner.builder()
                                        .format(Arrays.asList(Format.builder().w(468).h(60).build(),
                                                Format.builder().w(486).h(60).build()))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null,
                                        ExtImpConsumable.of(123, 234, 345, "unit"))))
                                .build()))
                .user(User.builder()
                        .ext(ExtUser.builder().consent("consent").build())
                        .build())
                .build();
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer)))
                .regs(Regs.of(null, ExtRegs.of(1, null)))
                .user(User.builder()
                        .ext(ExtUser.builder().consent("consent").build())
                        .build()))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("firstImp")
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(120).h(90).build()))
                        .build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpConsumable.of(111, 222, 333, "unit_name")))))
                .build();
    }

    private static ConsumableDecision givenDecision(
            Function<ConsumableDecision.ConsumableDecisionBuilder,
                    ConsumableDecision.ConsumableDecisionBuilder> decision) {

        return decision.apply(ConsumableDecision.builder())
                .build();
    }

    private static HttpCall<ConsumableBidRequest> givenHttpCall(
            Function<ConsumableBidResponse, ConsumableBidResponse> bidResponse,
            Function<ConsumableDecision.ConsumableDecisionBuilder,
                    ConsumableDecision.ConsumableDecisionBuilder> decision)
            throws JsonProcessingException {

        final String body = mapper.writeValueAsString(
                bidResponse.apply(ConsumableBidResponse.of(singletonMap("firstImp", givenDecision(decision)))));

        return HttpCall.success(
                HttpRequest.<ConsumableBidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static HttpCall<ConsumableBidRequest> givenHttpCall(Supplier<ConsumableBidResponse> bidResponseSupplier)
            throws JsonProcessingException {

        return HttpCall.success(
                HttpRequest.<ConsumableBidRequest>builder().build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponseSupplier.get())),
                null);
    }
}
