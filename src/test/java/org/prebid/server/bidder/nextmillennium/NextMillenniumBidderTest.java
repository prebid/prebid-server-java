package org.prebid.server.bidder.nextmillennium;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
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
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.nextmillennium.ExtImpNextMillennium;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class NextMillenniumBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com/";

    private NextMillenniumBidder nextMillenniumBidder;

    private static Imp givenImpWithExt(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                       ExtImpNextMillennium extImpNextMillennium) {

        return givenImp(impCustomizer.andThen(imp -> imp.ext(mapper.valueToTree(
                ExtPrebid.of(null, extImpNextMillennium))))::apply);
    }

    @Before
    public void setUp() {
        nextMillenniumBidder = new NextMillenniumBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NextMillenniumBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldUseBidRequestIdForAllRequests() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.id("id"),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, null)),
                givenImpWithExt(identity(), ExtImpNextMillennium.of(null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = nextMillenniumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty()
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(bidderError.getMessage()).startsWith("Cannot deserialize value of type");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = nextMillenniumBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), BidType.banner, "USD"));
    }

    @Test
    public void makeBidsWithZeroSeatBidsShouldReturnNoErrorsAndNoValues() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(Collections.emptyList())
                        .build()));

        // when
        final Result<List<BidderBid>> result = nextMillenniumBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(asList(imps))).build();
    }

    @Test
    public void makeBidsWithUnparsableBidResponseShouldReturnError() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.createArrayNode().toString());

        // when
        final Result<List<BidderBid>> result = nextMillenniumBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty()
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(bidderError.getMessage()).startsWith("Failed to decode:");
                });

    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .cur("USD")
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
