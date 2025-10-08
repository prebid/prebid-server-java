package org.prebid.server.bidder.rtbhouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.rtbhouse.ExtImpRtbhouse;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

@ExtendWith(MockitoExtension.class)
public class RtbhouseBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private RtbhouseBidder target;

    @BeforeEach
    public void setUp() {
        target = new RtbhouseBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RtbhouseBidder(
                "invalid_url",
                currencyConversionService,
                jacksonMapper));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(null));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().getFirst().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(null));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(null));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .video(Video.builder().build())
                        .build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyIfRequestCurrencyDoesNotMatchBidderCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .bidfloor(BigDecimal.ONE).bidfloorcur("EUR")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldTakePriceFloorsWhenBidfloorParamIsAlsoPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .bidfloor(BigDecimal.TEN).bidfloorcur("USD")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpRtbhouse.builder().bidFloor(BigDecimal.ONE).build())))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldTakeBidfloorExtImpParamIfNoBidfloorInRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpRtbhouse.builder().bidFloor(BigDecimal.valueOf(16)).build())))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(16));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageOnFailedCurrencyConversion() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willThrow(PreBidException.class);

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .bidfloor(BigDecimal.ONE).bidfloorcur("EUR")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).allSatisfy(bidderError -> {
            assertThat(bidderError.getType())
                    .isEqualTo(BidderError.Type.bad_input);
            assertThat(bidderError.getMessage())
                    .isEqualTo("Unable to convert provided bid floor currency from EUR to USD for imp `123`");
        });
    }

    @Test
    public void makeHttpRequestsShouldNotReturnErrorIfNativePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("123")
                                        .banner(null)
                                        .xNative(Native.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldParseNativeAdmData() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("123")
                                        .banner(null)
                                        .xNative(Native.builder().build()));
        final ObjectNode admNode = mapper.createObjectNode();
        final ObjectNode nativeNode = mapper.createObjectNode();
        nativeNode.put("property1", "value1");
        admNode.set("native", nativeNode);
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .adm(admNode.toString()))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("{\"property1\":\"value1\"}");
    }

    @Test
    public void makeBidsShouldReturnBidWithResolvedMacros() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(null));
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse(
                        bidBuilder -> bidBuilder
                                .nurl("nurl:${AUCTION_PRICE}")
                                .adm("adm:${AUCTION_PRICE}")
                                .price(BigDecimal.valueOf(12.34)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getNurl, Bid::getAdm)
                .containsExactly(tuple("nurl:12.34", "adm:12.34"));
    }

    @Test
    public void updateSitePublisherIdShouldReturnOriginalRequestWhenPublisherIdIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(Site.builder().id("site_id").build()),
                identity(),
                identity());

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, null);

        // then
        assertThat(result).isSameAs(bidRequest);
    }

    @Test
    public void updateSitePublisherIdShouldReturnNullWhenBidRequestIsNull() {
        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(null, "publisherId");

        // then
        assertThat(result).isNull();
    }

    @Test
    public void updateSitePublisherIdShouldReturnOriginalRequestWhenBothParametersAreNull() {
        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(null, null);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void updateSitePublisherIdShouldCreateSiteAndPublisherWhenBidRequestHasNoSite() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(null),
                identity(),
                identity());
        final String publisherId = "test_publisher_id";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result.getId()).isEqualTo("request_id");
        assertThat(result.getSite()).isNotNull();
        assertThat(result.getSite().getPublisher()).isNotNull();
        assertThat(result.getSite().getPublisher().getId()).isEqualTo(publisherId);
    }

    @Test
    public void updateSitePublisherIdShouldAddPublisherToExistingSiteWhenNoPublisher() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(Site.builder()
                                .id("site_id")
                                .name("site_name")
                                .domain("example.com")
                                .page("https://example.com/page")
                                .publisher(null)
                                .build()),
                identity(),
                identity());
        final String publisherId = "test_publisher_id";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result.getSite().getId()).isEqualTo("site_id");
        assertThat(result.getSite().getName()).isEqualTo("site_name");
        assertThat(result.getSite().getDomain()).isEqualTo("example.com");
        assertThat(result.getSite().getPage()).isEqualTo("https://example.com/page");
        assertThat(result.getSite().getPublisher()).isNotNull();
        assertThat(result.getSite().getPublisher().getId()).isEqualTo(publisherId);
    }

    @Test
    public void updateSitePublisherIdShouldUpdateExistingPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(Site.builder()
                                .id("site_id")
                                .publisher(Publisher.builder()
                                        .id("old_publisher_id")
                                        .build())
                                .build()),
                identity(),
                identity());
        final String publisherId = "new_publisher_id";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result.getSite().getId()).isEqualTo("site_id");
        assertThat(result.getSite().getPublisher().getId()).isEqualTo(publisherId);
    }

    @Test
    public void updateSitePublisherIdShouldPreserveOtherPublisherFieldsWhenUpdatingId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(Site.builder()
                                .id("site_id")
                                .publisher(Publisher.builder()
                                        .id("old_publisher_id")
                                        .name("publisher_name")
                                        .domain("publisher.com")
                                        .build())
                                .build()),
                identity(),
                identity());
        final String publisherId = "new_publisher_id";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result.getSite().getPublisher().getId()).isEqualTo(publisherId);
        assertThat(result.getSite().getPublisher().getName()).isEqualTo("publisher_name");
        assertThat(result.getSite().getPublisher().getDomain()).isEqualTo("publisher.com");
    }

    @Test
    public void updateSitePublisherIdShouldPreserveOtherSiteFieldsWhenAddingPublisher() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(Site.builder()
                                .id("site_id")
                                .name("site_name")
                                .domain("site.com")
                                .page("https://site.com/page")
                                .ref("https://referrer.com")
                                .build()),
                identity(),
                identity());
        final String publisherId = "test_publisher_id";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result.getSite().getId()).isEqualTo("site_id");
        assertThat(result.getSite().getName()).isEqualTo("site_name");
        assertThat(result.getSite().getDomain()).isEqualTo("site.com");
        assertThat(result.getSite().getPage()).isEqualTo("https://site.com/page");
        assertThat(result.getSite().getRef()).isEqualTo("https://referrer.com");
        assertThat(result.getSite().getPublisher().getId()).isEqualTo(publisherId);
    }

    @Test
    public void updateSitePublisherIdShouldPreserveOtherBidRequestFields() {
        // given
        final List<Imp> imps = List.of(
                givenImp(imp -> imp.id("imp1"), identity()),
                givenImp(imp -> imp.id("imp2"), identity())
        );
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .test(1)
                        .tmax(2000L)
                        .imp(imps)
                        .cur(List.of("USD", "EUR"))
                        .site(Site.builder().id("site_id").build()),
                identity(),
                identity());
        final String publisherId = "test_publisher_id";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result.getId()).isEqualTo("request_id");
        assertThat(result.getTest()).isEqualTo(1);
        assertThat(result.getTmax()).isEqualTo(2000L);
        assertThat(result.getImp()).hasSize(2);
        assertThat(result.getImp().get(0).getId()).isEqualTo("imp1");
        assertThat(result.getImp().get(1).getId()).isEqualTo("imp2");
        assertThat(result.getCur()).containsExactly("USD", "EUR");
        assertThat(result.getSite().getPublisher().getId()).isEqualTo(publisherId);
    }

    @Test
    public void updateSitePublisherIdShouldHandleEmptyPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(Site.builder().id("site_id").build()),
                identity(),
                identity());
        final String publisherId = "";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result.getSite().getPublisher().getId()).isEqualTo("");
    }

    @Test
    public void updateSitePublisherIdShouldCreateNewBidRequestInstance() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(Site.builder().id("site_id").build()),
                identity(),
                identity());
        final String publisherId = "test_publisher_id";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result).isNotSameAs(bidRequest);
        assertThat(result.getSite()).isNotSameAs(bidRequest.getSite());
    }

    @Test
    public void updateSitePublisherIdShouldHandleComplexPublisherObject() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidReq -> bidReq.id("request_id")
                        .site(Site.builder()
                                .id("site_id")
                                .publisher(Publisher.builder()
                                        .id("old_id")
                                        .name("Test Publisher")
                                        .domain("testpub.com")
                                        .build())
                                .build()),
                identity(),
                identity());
        final String publisherId = "complex_publisher_id_123";

        // when
        final BidRequest result = RtbhouseBidder.updateSitePublisherId(bidRequest, publisherId);

        // then
        assertThat(result.getSite().getPublisher().getId()).isEqualTo("complex_publisher_id_123");
        assertThat(result.getSite().getPublisher().getName()).isEqualTo("Test Publisher");
        assertThat(result.getSite().getPublisher().getDomain()).isEqualTo("testpub.com");
    }

    @Test
    public void makeHttpRequestsShouldAlwaysRemovePmpField() {
        // given - test with PMP containing deals
        final List<Deal> deals = List.of(
                Deal.builder().id("deal1").build(),
                Deal.builder().id("deal2").build()
        );
        final BidRequest bidRequestWithDeals = givenBidRequest(
                bidReq -> bidReq.id("request_id"),
                imp -> imp.id("123")
                        .pmp(Pmp.builder()
                                .privateAuction(1)
                                .deals(deals)
                                .build()),
                identity());

        // given - test with null PMP
        final BidRequest bidRequestWithNullPmp = givenBidRequest(
                bidReq -> bidReq.id("request_id"),
                imp -> imp.id("123").pmp(null),
                identity());

        // given - test with empty PMP
        final BidRequest bidRequestWithEmptyPmp = givenBidRequest(
                bidReq -> bidReq.id("request_id"),
                imp -> imp.id("123")
                        .pmp(Pmp.builder()
                                .privateAuction(0)
                                .deals(Collections.emptyList())
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> resultWithDeals = target.makeHttpRequests(bidRequestWithDeals);
        final Result<List<HttpRequest<BidRequest>>> resultWithNullPmp = target.makeHttpRequests(bidRequestWithNullPmp);
        final Result<List<HttpRequest<BidRequest>>> resultWithEmptyPmp =
                target.makeHttpRequests(bidRequestWithEmptyPmp);

        // then - all should have PMP completely removed (null)
        assertThat(resultWithDeals.getErrors()).isEmpty();
        assertThat(resultWithDeals.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .containsOnlyNulls();

        assertThat(resultWithNullPmp.getErrors()).isEmpty();
        assertThat(resultWithNullPmp.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .containsOnlyNulls();

        assertThat(resultWithEmptyPmp.getErrors()).isEmpty();
        assertThat(resultWithEmptyPmp.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .containsOnlyNulls();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpRtbhouse.ExtImpRtbhouseBuilder, ExtImpRtbhouse.ExtImpRtbhouseBuilder> extCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                Function<ExtImpRtbhouse.ExtImpRtbhouseBuilder,
                                        ExtImpRtbhouse.ExtImpRtbhouseBuilder> extCustomizer) {

        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                extCustomizer.apply(ExtImpRtbhouse.builder()
                                                .publisherId("publisherId")
                                                .region("region"))
                                        .build()))))
                .build();
    }
}
