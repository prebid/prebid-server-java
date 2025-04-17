package org.prebid.server.bidder.nextmillennium;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.nextmillennium.ExtImpNextMillennium;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@ExtendWith(MockitoExtension.class)
public class NextMillenniumBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com/";

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    private NextMillenniumBidder target;

    @BeforeEach
    public void setUp() {
        target = new NextMillenniumBidder(
                ENDPOINT_URL,
                jacksonMapper,
                List.of("valueOne", "valueTwo"),
                prebidVersionProvider);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new NextMillenniumBidder(
                        "invalid_url",
                        jacksonMapper,
                        List.of("valueOne", "valueTwo"),
                        prebidVersionProvider));
    }

    @Test
    public void makeHttpRequestsShouldUseBidRequestIdForAllRequests() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.id("id"),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, null)),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getId)
                .containsExactly("id", "id");
    }

    @Test
    public void makeHttpRequestsShouldUseBidRequestTestForAllRequests() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.test(1),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, null)),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTest)
                .containsExactly(1, 1);
    }

    @Test
    public void makeHttpRequestsShouldUseImpExtBidderPlacementIdForStoredRequestId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImpWithExt(identity(), ExtImpNextMillennium.of("placement1", null)),
                givenImpWithExt(identity(), ExtImpNextMillennium.of("placement2", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getStoredrequest)
                .extracting(ExtStoredRequest::getId)
                .containsExactly("placement1", "placement2");
    }

    @Test
    public void makeHttpRequestsShouldUseImpExtBidderGroupIdForStoredRequestId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, "group1")),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, "group2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getStoredrequest)
                .extracting(ExtStoredRequest::getId)
                .containsExactly("ggroup1;;", "ggroup2;;");
    }

    @Test
    public void makeHttpRequestsShouldUseFirstImpBannerFirstFormatForStoredRequestIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImpWithExt(
                        imp -> imp.banner(Banner.builder()
                                .format(asList(
                                        Format.builder().w(1).h(2).build(),
                                        Format.builder().w(3).h(4).build()))
                                .w(5)
                                .h(6)
                                .build()),
                        ExtImpNextMillennium.of(null, "group1")),
                givenImpWithExt(
                        imp -> imp.banner(Banner.builder().w(7).h(8).build()),
                        ExtImpNextMillennium.of(null, "group2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getStoredrequest)
                .extracting(ExtStoredRequest::getId)
                .containsExactly("ggroup1;1x2;", "ggroup2;1x2;");
    }

    @Test
    public void makeHttpRequestsShouldUseFirstImpBannerSizeForStoredRequestIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImpWithExt(
                        imp -> imp.banner(Banner.builder().w(7).h(8).build()),
                        ExtImpNextMillennium.of(null, "group1")),
                givenImpWithExt(
                        imp -> imp.banner(Banner.builder()
                                .format(singletonList(Format.builder().w(1).h(2).build()))
                                .build()),
                        ExtImpNextMillennium.of(null, "group2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getStoredrequest)
                .extracting(ExtStoredRequest::getId)
                .containsExactly("ggroup1;7x8;", "ggroup2;7x8;");
    }

    @Test
    public void makeHttpRequestsShouldUseAppDomainForStoredRequestId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder().domain("appDomain").build()),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, "group1")),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, "group2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getStoredrequest)
                .extracting(ExtStoredRequest::getId)
                .containsExactly("ggroup1;;appDomain", "ggroup2;;appDomain");
    }

    @Test
    public void makeHttpRequestsShouldUseSiteDomainForStoredRequestId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("siteDomain").build()),
                givenImpWithExt(identity(), ExtImpNextMillennium.of("placement1", "group1")),
                givenImpWithExt(identity(), ExtImpNextMillennium.of("placement2", "group2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getStoredrequest)
                .extracting(ExtStoredRequest::getId)
                .containsExactly("ggroup1;;siteDomain", "ggroup2;;siteDomain");
    }

    @Test
    public void makeHttpRequestsWithInvalidImpsShouldReturnError() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty()
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(bidderError.getMessage()).startsWith("Cannot deserialize value of type");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsImpExtComparison() {
        // given
        final String placementId = "6821";
        final Imp givenImp = givenImpWithExt(imp -> imp
                        .id("custom_imp_id")
                        .tagid("custom_imp_tagid")
                        .secure(1)
                        .banner(Banner.builder()
                                .w(728)
                                .h(90)
                                .format(singletonList(Format.builder()
                                        .w(728)
                                        .h(90)
                                        .build()))
                                .build()),
                ExtImpNextMillennium.of(placementId, null));

        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .schains(emptyList())
                .targeting(ExtRequestTargeting.builder()
                        .includewinners(true)
                        .includebidderkeys(false)
                        .build())
                .build());

        final BidRequest bidRequest = givenBidRequest(b -> b
                .id("c868fd0b-960c-4f49-a8d6-2b3e938b41f2")
                .test(1)
                .tmax(845L)
                .device(Device.builder().w(994).h(1768).build())
                .imp(singletonList(givenImp))
                .ext(extRequest)
                .site(Site.builder().page("https://www.advfn.com").domain("www.advfn.com").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getStoredrequest)
                .extracting(ExtStoredRequest::getId)
                .containsExactly(placementId);
    }

    @Test
    public void makeHttpRequestsImpShouldBeIdenticalExceptExt() {
        // given
        final String placementId = "6821";
        final List<Format> bannerFormat = singletonList(Format.builder()
                .w(728)
                .h(90)
                .build());

        final Banner banner = Banner.builder()
                .w(728)
                .h(90)
                .format(bannerFormat)
                .build();

        final Imp givenImp = givenImpWithExt(imp -> imp
                        .id("custom_imp_id")
                        .tagid("custom_imp_tagid")
                        .secure(1)
                        .banner(banner),
                ExtImpNextMillennium.of(placementId, null));

        final BidRequest bidRequest = givenBidRequest(b -> b
                .id("c868fd0b-960c-4f49-a8d6-2b3e938b41f2")
                .test(1)
                .imp(singletonList(givenImp)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(List::getFirst)
                .element(0)
                .usingRecursiveComparison()
                .ignoringFields("ext")
                .isEqualTo(givenImp);
    }

    @Test
    public void makeHttpRequestsBidRequestShouldBeIdenticalExceptImpExt() {
        // given
        final String placementId = "6821";
        final List<Format> bannerFormat = singletonList(Format.builder()
                .w(728)
                .h(90)
                .build());
        final Banner banner = Banner.builder()
                .w(728)
                .h(90)
                .format(bannerFormat)
                .build();

        final Imp initialImp = givenImpWithExt(imp -> imp
                        .id("custom_imp_id")
                        .tagid("custom_imp_tagid")
                        .secure(1)
                        .banner(banner),
                ExtImpNextMillennium.of(placementId, null));

        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .schains(emptyList())
                .targeting(ExtRequestTargeting.builder()
                        .includewinners(true)
                        .includebidderkeys(false)
                        .build())
                .build());

        final BidRequest bidRequest = givenBidRequest(b -> b
                .id("c868fd0b-960c-4f49-a8d6-2b3e938b41f2")
                .test(1)
                .tmax(845L)
                .device(Device.builder().w(994).h(1768).build())
                .imp(singletonList(initialImp))
                .ext(extRequest)
                .site(Site.builder().page("https://www.advfn.com").domain("www.advfn.com").build()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .element(0)
                .usingRecursiveComparison()
                .ignoringFields("imp", "ext")
                .isEqualTo(bidRequest);
    }

    @Test
    public void makeHttpRequestsShouldPreserveExtPrebidServer() {
        // given
        final ExtRequestPrebidServer extRequestPrebidServer = ExtRequestPrebidServer.of(
                "http://localhost:8080",
                1,
                "dc-test",
                "/openrtb2/auction");

        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .server(extRequestPrebidServer)
                .build());

        final BidRequest bidRequest = BidRequest.builder()
                .id("test-request")
                .imp(singletonList(givenImpWithExt(
                        imp -> imp.banner(Banner.builder().w(300).h(250).build()),
                        ExtImpNextMillennium.of("placement_id", null))))
                .ext(extRequest)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final BidRequest resultingBidRequest = result.getValue().get(0).getPayload();

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getServer)
                .containsExactly(extRequestPrebidServer);
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMTypeIsOne() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.mtype(1).impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(1).impid("123").build(),
                        BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenMTypeIsTwo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.mtype(2).impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(2).impid("123").build(),
                        BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMTypeIsUnknown() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.mtype(999).impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors())
                .contains(BidderError.badServerResponse("Unable to fetch mediaType 999 in multi-format: 123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMTypeIsMissing() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.id("bidId").impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).contains(BidderError.badServerResponse("Missing MType for bid: bidId"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBothValidAndInvalidBidderBidAtTheSameTime() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(
                        bidBuilder -> bidBuilder.mtype(999).impid("123"),
                        bidBuilder -> bidBuilder.mtype(1).impid("312"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors())
                .contains(BidderError.badServerResponse("Unable to fetch mediaType 999 in multi-format: 123"));
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().mtype(1).impid("312").build(),
                        BidType.banner, "USD"));
    }

    @Test
    public void makeBidsWithZeroSeatBidsShouldReturnNoErrorsAndNoValues() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(emptyList())
                        .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsWithUnparsableBidResponseShouldReturnError() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.createArrayNode().toString());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty()
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(bidderError.getMessage()).startsWith("Failed to decode:");
                });
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(asList(imps))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    @SafeVarargs
    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(Arrays.stream(bidCustomizers)
                                .map(bidCustomizer -> bidCustomizer.apply(Bid.builder()).build())
                                .toList())
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static Imp givenImpWithExt(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                       ExtImpNextMillennium extImpNextMillennium) {
        return givenImp(impCustomizer.andThen(imp -> imp.ext(mapper.valueToTree(
                ExtPrebid.of(null, extImpNextMillennium))))::apply);
    }
}
