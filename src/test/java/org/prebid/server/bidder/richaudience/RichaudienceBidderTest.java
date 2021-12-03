package org.prebid.server.bidder.richaudience;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.richaudience.ExtImpRichaudience;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class RichaudienceBidderTest extends VertxTest {

    private RichaudienceBidder bidder;

    @Before
    public void setUp() {
        bidder = new RichaudienceBidder("https://test.domain.dm/uri", jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RichaudienceBidder("incorrect_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.device(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput("Device IP is required."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceIpIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.device(Device.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput("Device IP is required."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSomeImpBannerIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()), givenImp(imp -> imp.banner(null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Banner W/H/Format is required. ImpId: null"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSomeImpBannerIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(imp -> imp.banner(Banner.builder().build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Banner W/H/Format is required. ImpId: null"));
    }

    @Test
    public void makeHttpRequestsShouldSetImpSecureToFalseIfSitePageIsInsecure() {
        final BidRequest bidRequest = givenBidRequest("http://insecure.site.st/uri",
                givenImp(identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2)
                .extracting(Imp::getSecure)
                .allMatch(Integer.valueOf(0)::equals);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetImpSecureToTrueIfSitePageIsSecure() {
        final BidRequest bidRequest = givenBidRequest("https://secure.site.st/uri",
                givenImp(identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2)
                .extracting(Imp::getSecure)
                .allMatch(Integer.valueOf(1)::equals);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetImpTagIdIfImpExtPidIsPresent() {
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(ext -> ext.pid("123"), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2)
                .extracting(Imp::getTagid)
                .containsExactly(null, "123");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectImpBidFloorCur() {
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(imp -> imp.bidfloorcur("RUB")),
                givenImp(ext -> ext.bidFloorCur("UAH"), identity()),
                givenImp(ext -> ext.bidFloorCur("BYN"), imp -> imp.bidfloorcur("RUB")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(4)
                .extracting(Imp::getBidfloorcur)
                .containsExactly("USD", "RUB", "UAH", "BYN");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetTestToTrueIfSomeImpExtTestIsTrue() {
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(ext -> ext.test(true), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTest)
                .containsExactly(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetSiteDomainIfItIsAbsentAndSitePageIsPresent() {
        final BidRequest bidRequest = givenBidRequest("https://domain.dm/uri", givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getDomain)
                .containsExactly("domain.dm");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("Incorrect body");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("null");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("{}");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        final BidRequest.BidRequestBuilder builder = BidRequest.builder()
                .device(Device.builder().ip("Some ip.").build());
        return bidRequestCustomizer.apply(builder).build();
    }

    private BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(request -> request.imp(List.of(imps)));
    }

    private BidRequest givenBidRequest(String url, Imp... imps) {
        return givenBidRequest(request -> request.site(Site.builder().page(url).build()).imp(List.of(imps)));
    }

    private Imp givenImp(UnaryOperator<ExtImpRichaudience.ExtImpRichaudienceBuilder> extCustomizer,
                         UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        final ExtImpRichaudience extImpRichaudience = extCustomizer.apply(ExtImpRichaudience.builder()).build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpRichaudience));
        final Imp.ImpBuilder builder = Imp.builder()
                .banner(Banner.builder().w(21).h(9).build())
                .ext(ext);

        return impCustomizer.apply(builder).build();
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(identity(), impCustomizer);
    }

    private HttpCall<BidRequest> givenHttpCall(String body) {
        final HttpResponse response = HttpResponse.of(200, null, body);
        return HttpCall.success(null, response, null);
    }
}
