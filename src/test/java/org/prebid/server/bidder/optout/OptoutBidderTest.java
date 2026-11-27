package org.prebid.server.bidder.optout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.optout.model.CurrencySettings;
import org.prebid.server.bidder.optout.model.OptoutBid;
import org.prebid.server.bidder.optout.model.OptoutRequest;
import org.prebid.server.bidder.optout.model.OptoutResponse;
import org.prebid.server.bidder.optout.model.OptoutSlot;
import org.prebid.server.bidder.viant.ViantBidder;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.optout.ExtImpOptout;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class OptoutBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private OptoutBidder target;

    @BeforeEach
    public void setUp() {
        target = new OptoutBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ViantBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestShouldReturnErrorWhenAllImpsAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<OptoutRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldAddErrorsOnInvalidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                givenImp(imp -> imp.ext(givenImpExt("", null))),
                givenImp(identity()),
                givenImp(imp -> imp.ext(givenImpExt(null, null))),
                givenImp(imp -> imp.ext(givenImpExt("", null))),
                givenImp(imp -> imp.ext(givenImpExt("publisherId", null))));

        // when
        final Result<List<HttpRequest<OptoutRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(OptoutRequest::getSlots)
                .hasSize(2);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .hasSize(4)
                .satisfies(errors ->
                        assertThat(errors.getFirst()).startsWith("invalid impression extension:"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExtImpData() {
        // given
        final ObjectNode customs = mapper.createObjectNode().put("custom1", "customValue")
                .set("custom2", mapper.createArrayNode().add("customListValue").add(227));

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(
                        givenImpExt(ExtImpOptout.of("publisher", "adslot", customs, true)))));

        // when
        final Result<List<HttpRequest<OptoutRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(OptoutRequest::getPublisher)
                .singleElement()
                .isEqualTo("publisher");

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(OptoutRequest::getOrtb2)
                .singleElement()
                .isNotNull();

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(OptoutRequest::getSlots)
                .extracting(OptoutSlot::getCustoms)
                .containsExactly(Map.of("custom1", "customValue", "custom2", "customListValue,227"));

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(OptoutRequest::getSlots)
                .extracting(OptoutSlot::getAdSlot)
                .singleElement()
                .isEqualTo("adslot");
    }

    @Test
    public void makeHttpRequestShouldReturnOSWhenMayForwardPersonalData() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder()
                        .os("ios")
                        .build()),
                givenImp(imp -> imp.ext(
                        givenImpExt("publisher", "adslot"))));

        // when
        final Result<List<HttpRequest<OptoutRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(OptoutRequest::getOs)
                .singleElement()
                .isEqualTo("ios");
    }

    @Test
    public void makeHttpRequestShouldReturnHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .device(Device.builder().ua("userAgent").ip("127.0.0.1").build())
                        .regs(Regs.builder().gdpr(0).build()),
                givenImp(imp -> imp.ext(
                        givenImpExt("publisher", "adslot"))));

        // when
        final Result<List<HttpRequest<OptoutRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .extracting(map -> map.get("Content-Type"))
                .containsOnly("application/json;charset=utf-8");

        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .extracting(map -> map.get("X-Device-User-Agent"))
                .containsOnly("userAgent");

        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .extracting(map -> map.get("X-Forwarded-For"))
                .containsOnly("127.0.0.1");
    }

    @Test
    public void makeBidsShouldReturnFromAllSeatBid() throws JsonProcessingException {
        // given
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")),
                givenSlot(slot -> slot.id("456")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(
                        givenOptoutBid("123", "bid-1"),
                        givenOptoutBid("456", "bid-2")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("bid-1", "bid-2");
    }

    @Test
    public void makeBidsShouldReturnErrorForUnknownImpressionID() throws JsonProcessingException {
        // given
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("789")),
                givenSlot(slot -> slot.id("456")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(
                        givenOptoutBid("123", "bid-1"),
                        givenOptoutBid("456", "bid-2")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("optout: unknown impression ID");
                });
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bid-2");
    }

    @Test
    public void makeBidsShouldParseOptoutBidsAsList() throws JsonProcessingException {
        // given
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")),
                givenSlot(slot -> slot.id("456")));
        final String optoutBidsListResponse = mapper.writeValueAsString(List.of(
                givenOptoutBid("123", "bid-1").apply(OptoutBid.builder()).build(),
                givenOptoutBid("456", "bid-2").apply(OptoutBid.builder()).build()
        ));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(bidRequest, optoutBidsListResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("bid-1", "bid-2");

    }

    @Test
    public void makeBidsShouldReturnErrorIfBidCurrencyIsBlank() throws JsonProcessingException {
        // given
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(
                        bid -> bid.requestId("123").bidId("bid-1").currency("")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("optout: invalid bid for impression");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidAdIsBlank() throws JsonProcessingException {
        // given
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(
                        bid -> bid.requestId("123").bidId("bid-1").currency("EUR").ad("")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("optout: invalid bid for impression"); });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidWidthLessThanZero() throws JsonProcessingException {
        // given
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(
                        bid -> bid.requestId("123").bidId("bid-1").currency("EUR").ad("ad").width(-100)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("optout: invalid bid for impression");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidHeightLessThanZero() throws JsonProcessingException {
        // given
        final OptoutBid.OptoutBidBuilder bidBuilder = OptoutBid.builder()
                .requestId("123")
                .bidId("bid-1")
                .currency("EUR")
                .ad("ad")
                .width(100)
                .height(-100);
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(_ -> bidBuilder));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("optout: invalid bid for impression");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidCpmLessThanZero() throws JsonProcessingException {
        // given
        final OptoutBid.OptoutBidBuilder bidBuilder = OptoutBid.builder()
                .requestId("123")
                .bidId("bid-1")
                .currency("EUR")
                .ad("ad")
                .width(100)
                .height(100)
                .cpm(BigDecimal.valueOf(-2.3));
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(_ -> bidBuilder));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("optout: invalid bid for impression");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidIdIsBlank() throws JsonProcessingException {
        // given
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(givenOptoutBid("123", null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("optout: missing bid ID for impression");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorForMixedResponseCurrencies() throws JsonProcessingException {
        // given
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")),
                givenSlot(slot -> slot.id("456")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(givenOptoutBid("123", "bid0-1", "JPY"),
                        givenOptoutBid("456", "bid-2", "USD")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("optout: mixed response currencies");
                });
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldUseDefaultTllIfBidTtlIsNull() throws JsonProcessingException {
        // given
        final OptoutBid.OptoutBidBuilder bidBuilder = OptoutBid.builder()
                .requestId("123")
                .bidId("bid-1")
                .currency("EUR")
                .ad("ad")
                .width(100)
                .height(100)
                .cpm(BigDecimal.valueOf(2.3));
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(_ -> bidBuilder));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExp)
                .containsOnly(300);
    }

    @Test
    public void makeBidsShouldCreateValidBidderBid() throws JsonProcessingException {
        // given
        final OptoutBid.OptoutBidBuilder bidBuilder = OptoutBid.builder()
                .requestId("123")
                .bidId("bid-1")
                .currency("EUR")
                .ad("ad")
                .width(100)
                .height(200)
                .cpm(BigDecimal.valueOf(2.3))
                .creativeId("789")
                .ttl(567);
        final OptoutRequest bidRequest = givenOptoutRequest(
                givenSlot(slot -> slot.id("123")));
        final BidderCall<OptoutRequest> httpCall = givenHttpCall(
                bidRequest,
                givenOptoutResponse(_ -> bidBuilder));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .containsExactly(Bid.builder().id("bid-1").impid("123").price(BigDecimal.valueOf(2.3))
                        .adm("ad").w(100).h(200).crid("789").exp(567).build());
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              Imp... imps) {
        return requestCustomizer.apply(BidRequest.builder()
                        .cur(List.of("USD"))
                        .site(Site.builder()
                                .page("https://example.test/story")
                                .build())
                        .app(App.builder().build())
                        .user(User.builder().ext(ExtUser.builder()
                                .data(mapper.createObjectNode())
                                .build()).build())
                        .imp(List.of(imps)))
                .build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("imp-1")
                        .banner(Banner.builder().w(100).h(200).build())
                        .ext(givenImpExt(ExtImpOptout.of("placementId", "placementId", null, false))))
                .build();
    }

    private static ObjectNode givenImpExt(String publisher, String adSlot) {
        return givenImpExt(ExtImpOptout.of(publisher, adSlot, null, false));
    }

    private static ObjectNode givenImpExt(ExtImpOptout extImpOptout) {
        return mapper.valueToTree(ExtPrebid.of(null, extImpOptout));
    }

    private static OptoutRequest givenOptoutRequest(UnaryOperator<OptoutRequest.OptoutRequestBuilder> requestCustomizer,
                                                    OptoutSlot... slots) {
        return requestCustomizer.apply(OptoutRequest.builder()
                .publisher("publisher")
                .slots(List.of(slots))
                .cur(CurrencySettings.builder()
                        .adServerCurrency("EUR")
                        .granularityMultiplier(1)
                        .build())
                .sdkVersion("prebid-server")
                .gdpr(0)
        ).build();
    }

    private static OptoutRequest givenOptoutRequest(OptoutSlot... slots) {
        return givenOptoutRequest(identity(), slots);
    }

    private static OptoutSlot givenSlot(UnaryOperator<OptoutSlot.OptoutSlotBuilder> slotCustomizer) {
        return slotCustomizer.apply(OptoutSlot.builder()
                        .id("123")
                        .adSlot("adSlot"))
                .build();
    }

    @SafeVarargs
    @SneakyThrows
    private String givenOptoutResponse(UnaryOperator<OptoutBid.OptoutBidBuilder>... bidCustomizers) {
        return mapper.writeValueAsString(
                OptoutResponse.of(Arrays.stream(bidCustomizers)
                        .map(bid -> bid.apply(OptoutBid.builder())
                                .build())
                        .toList()));
    }

    private UnaryOperator<OptoutBid.OptoutBidBuilder> givenOptoutBid(String requestId, String bidId, String currency) {
        return _ -> OptoutBid.builder()
                .requestId(requestId)
                .bidId(bidId)
                .currency(currency)
                .height(100)
                .width(200)
                .cpm(BigDecimal.valueOf(1.2))
                .ad("ad");
    }

    private UnaryOperator<OptoutBid.OptoutBidBuilder> givenOptoutBid(String requestId, String bidId) {
        return givenOptoutBid(requestId, bidId, "EUR");
    }

    private static BidderCall<OptoutRequest> givenHttpCall(OptoutRequest request, String response) {
        return BidderCall.succeededHttp(
                HttpRequest.<OptoutRequest>builder().payload(request).build(),
                HttpResponse.of(200, null, response),
                null);
    }
}
