package org.prebid.server.bidder.peak226;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.peak226.model.Peak226Response;
import org.prebid.server.bidder.peak226.model.Peak226SeatBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.peak226.ExtImpPeak226;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class Peak226BidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{Region}.test.endpoint.com";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String ZERO_IFA = "00000000-0000-0000-0000-000000000000";
    private static final String DEFAULT_REGION = "us";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private Peak226Bidder target;

    @BeforeEach
    public void setUp() {
        target = new Peak226Bidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Peak226Bidder("invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsWithoutRequestWhenAllImpsAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldAddErrorsOnInvalidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                givenImp(imp -> imp.ext(
                        givenImpExt(ExtImpPeak226.of(null, null, DEFAULT_REGION)))),
                givenImp(imp -> imp.ext(
                        givenImpExt(ExtImpPeak226.of("", null, DEFAULT_REGION)))),
                // valid imps
                givenImp(identity()),
                givenImp(imp -> imp.tagid("test-tag").ext(
                        givenImpExt(ExtImpPeak226.of(null, null, DEFAULT_REGION)))),
                givenImp(imp -> imp.tagid("test-tag").ext(
                        givenImpExt(ExtImpPeak226.of("", null, DEFAULT_REGION)))),
                givenImp(imp -> imp.ext(
                        givenImpExt(ExtImpPeak226.of("publisherId", null, null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(6);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .hasSize(1)
                .satisfies(errors ->
                        assertThat(errors.getFirst()).startsWith("Invalid ext. Imp.Id:"));
    }

    @Test
    public void makeHttpRequestsShouldPreferPlacementIdOverTagId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .tagid("tagId")
                        .ext(givenImpExt(ExtImpPeak226.of("publisherId", "placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("placementId");
    }

    @Test
    public void makeHttpRequestsShouldStripBidderExt() {
        // given
        ObjectNode extImp = givenImpExt(ExtImpPeak226.of("publisherId", null, null))
                .put("pid", "testValue");
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.tagid("tagId").ext(extImp)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .singleElement()
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("bidder"))
                .isNull();
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorIfCurrencyIsDifferent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("EUR")));

        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "EUR", DEFAULT_BID_CURRENCY))
                .willReturn(BigDecimal.ONE);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.ONE, DEFAULT_BID_CURRENCY));
    }

    @Test
    public void makeHttpRequestsShouldNotConvertBidfloorAndAssignUSDCurrencyWhenBidfloorHasEmptyCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur(null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, DEFAULT_BID_CURRENCY));
    }

    @Test
    public void makeHttpRequestShouldPreferImpExtRegionOverDefaultRegion() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp ->
                        imp.ext(givenImpExt(ExtImpPeak226.of("publisher", "placementId", "eu")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eu.test.endpoint.com");
    }

    @Test
    public void makeHttpRequestShouldSetDefaultRegionWhenImpExtRegionHasEmptyValue() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp ->
                        imp.ext(givenImpExt(ExtImpPeak226.of("publisher", "placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://%s.test.endpoint.com".formatted(DEFAULT_REGION));
    }

    @Test
    public void makeHttpRequestShouldSanitizeDevice() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder().ifa(ZERO_IFA).build()),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getDevice)
                .singleElement()
                .extracting(Device::getIfa)
                .isEqualTo("");
    }

    @Test
    public void makeHttpRequestsShouldModifySiteAndAppPublisher() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .tagid("tagId")
                        .ext(givenImpExt(ExtImpPeak226.of("newPublisher", null, null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .singleElement()
                .extracting(Publisher::getId)
                .isEqualTo("newPublisher");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .singleElement()
                .extracting(Publisher::getId)
                .isEqualTo("newPublisher");
    }

    @Test
    public void makeBidsShouldReturnBidsFromAllSeatBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(givenBidNode("bid-1"), givenBidNode("bid-2"))));

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
    public void makeBidShouldAdmOverAdmNative() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(givenBidNode("bid-1"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("https://https://test_adm.com?price=1.5");
    }

    @Test
    public void makeBidShouldUseAdmNativeWhenAdmIsEmpty() throws JsonProcessingException {
        // given
        final ObjectNode bidNode = givenBidNode("bid-1");
        bidNode.remove("adm");
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bidNode)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("{\"admNativeProp\":\"1.5\"}");
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              Imp... imps) {
        return requestCustomizer.apply(BidRequest.builder()
                        .cur(List.of("USD"))
                        .site(Site.builder().build())
                        .app(App.builder().build())
                        .imp(List.of(imps)))
                .build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(givenImpExt(ExtImpPeak226.of("placementId", "placementId", "us"))))
                .build();
    }

    private static ObjectNode givenImpExt(ExtImpPeak226 extImpPeak226) {
        return mapper.valueToTree(ExtPrebid.of(null, extImpPeak226));
    }

    private Peak226Response givenBidResponse(ObjectNode... objectNode) {
        return Peak226Response.of(null, singletonList(Peak226SeatBid.of(List.of(objectNode))));
    }

    private ObjectNode givenBidNode(String id) {
        final ObjectNode bidNode = mapper.createObjectNode();
        bidNode.put("id", id);
        bidNode.put("price", "1.5");
        bidNode.put("adm", "https://https://test_adm.com?price=${AUCTION_PRICE}");
        bidNode.set("adm_native",
                mapper.createObjectNode().put("admNativeProp", "${AUCTION_PRICE}"));
        bidNode.put("nurl", "https://test.com?price=${AUCTION_PRICE}");
        bidNode.put("burl", "https://test.com?price=${AUCTION_PRICE}");
        bidNode.put("mtype", "1");
        return bidNode;
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
