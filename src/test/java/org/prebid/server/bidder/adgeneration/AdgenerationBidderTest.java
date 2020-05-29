package org.prebid.server.bidder.adgeneration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adgeneration.model.AdgenerationResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adgeneration.ExtImpAdgeneration;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class AdgenerationBidderTest extends VertxTest {
    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private AdgenerationBidder adgenerationBidder;

    @Before
    public void setUp() {
        adgenerationBidder = new AdgenerationBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdgenerationBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpressionListSizeIsZero() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adgenerationBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("No impression in the bid request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = adgenerationBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdgeneration.of(null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adgenerationBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("No Location ID in ExtImpAdgeneration."));
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpsUrlIfAtLeastOneImpIsSecured() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adgenerationBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/?posall=SSPLOC&id=123&sdktype=0&hb=true&t=json3&"
                        + "currency=JPY&sdkname=prebidserver&adapterver=1.0.0");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = adgenerationBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseWithNoContent() {
        // given
        final HttpCall<BidRequest> httpCall = HttpCall
                .success(null, HttpResponse.of(204, null, null), null);

        // when
        final Result<List<BidderBid>> result = adgenerationBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .build();
        final AdgenerationResponse adgenerationResponse = AdgenerationResponse.of("123", "dealid", "ad", "beacon",
                "baconurl", BigDecimal.valueOf(10), "creativeid", 100, 200, 50, "vastxml", "landingUrl",
                "scheduleid", null);

        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(adgenerationResponse));

        // when
        final Result<List<BidderBid>> result = adgenerationBidder.makeBids(httpCall, bidRequest);

        // then
        final String adm = "<div id=\"apvad-123\"></div><script type=\"text/javascript\" id=\"apv\" src=\"https://cdn.apvdr.com/js/VideoAd.min.js\"></script><script type=\"text/javascript\"> (function(){ new APV.VideoAd({s:\"123\"}).load('vastxml'); })(); </script>beacon</body>";
        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("123")
                        .impid("123")
                        .price(BigDecimal.valueOf(10))
                        .crid("creativeid")
                        .dealid("dealid")
                        .w(200)
                        .h(100)
                        .adm(adm)
                        .build(),
                BidType.banner, "USD");

        assertThat(result.getValue().get(0).getBid().getAdm()).isEqualTo(adm);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0).isEqualTo(expected);
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(adgenerationBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpAdgeneration.of("123")))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
