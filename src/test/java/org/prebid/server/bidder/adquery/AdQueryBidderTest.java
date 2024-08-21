package org.prebid.server.bidder.adquery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adquery.model.request.AdQueryRequest;
import org.prebid.server.bidder.adquery.model.response.AdQueryDataResponse;
import org.prebid.server.bidder.adquery.model.response.AdQueryMediaType;
import org.prebid.server.bidder.adquery.model.response.AdQueryResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adquery.ExtImpAdQuery;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class AdQueryBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private final AdQueryBidder target = new AdQueryBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AdQueryBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectPopulateAdQueryRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(AdQueryRequest.builder()
                        .version("server")
                        .placementCode("pl1")
                        .auctionId("")
                        .type("6d93f2a0e5f0fe2cc3a6e9e3ade964b43b07f897")
                        .adUnitCode("test-banner-imp-id")
                        .bidQid("user-id")
                        .bidId("22e26bd9a702bc1")
                        .bidIp("31.21.32.42")
                        .bidIpv6("131.123.632.32")
                        .bidUa("ua-test-value")
                        .bidder("adquery")
                        .bidPageUrl("https://www.unknowsite.com")
                        .bidderRequestId("22e26bd9a702bc")
                        .bidderRequestsCount(1)
                        .bidRequestsCount(1)
                        .sizes("320x100,300x250")
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).first()
                .satisfies(error -> assertThat(error.getMessage()).startsWith("Cannot deserialize value of type"));
    }

    @Test
    public void makeHttpRequestsShouldCorrectPopulateHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        // then
        assertThat(result.getValue())
                .hasSize(1)
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "31.21.32.42"));
    }

    @Test
    public void makeHttpRequestsShouldNotPopulateXForwardedHeaderWhenDeviceDoesNotContainIp() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.device(Device.builder().ip(null).build()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .hasSize(1)
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestsShouldNotPopulateSeveralFieldWhenDeviceIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.device(null),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(AdQueryRequest::getBidIp, AdQueryRequest::getBidIpv6, AdQueryRequest::getBidUa)
                .containsExactly(Tuple.tuple(null, null, null));
    }

    @Test
    public void makeHttpRequestsShouldNotPopulateBidPageUrlFieldWhenSitePageIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.site(null),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(AdQueryRequest::getBidPageUrl)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldPopulateSizesFieldWithEmptyStringWhenBannerFormatIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder().format(null).build())));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(AdQueryRequest::getSizes)
                .containsExactly("");
    }

    @Test
    public void makeHttpRequestsShouldCorrectPopulateSizesFieldWhenBannerContainWidthAndHeight() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                givenImp(impBuilder -> impBuilder.banner(Banner.builder().h(100).w(210).format(null).build())));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(AdQueryRequest::getSizes)
                .containsExactly("210x100");
    }

    @Test
    public void makeHttpRequestsShouldPopulateSizesFieldWithEmptyStringWhenBannerNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                givenImp(impBuilder -> impBuilder.banner(null)));

        // when
        final Result<List<HttpRequest<AdQueryRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(AdQueryRequest::getSizes)
                .containsExactly("");
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<AdQueryRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfAdQueryResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<AdQueryRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().id("requestId").build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfAdQueryRequestDataIsNull() throws JsonProcessingException {
        // given
        final BidderCall<AdQueryRequest> httpCall = givenHttpCall(mapper.writeValueAsString(AdQueryResponse.of(null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().id("requestId").build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenAdQueryResponseMediaTypeDoesNotContainBannerType()
            throws JsonProcessingException {
        // given
        final BidderCall<AdQueryRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(AdQueryResponse.of(AdQueryDataResponse.builder()
                        .requestId("abs")
                        .cpm(BigDecimal.valueOf(123))
                        .adQueryMediaType(AdQueryMediaType.of(BidType.audio, 120, 320))
                        .build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().id("abs1").build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Unsupported MediaType: audio");
                });
    }

    @Test
    public void makeBidsShouldReturnCorrectResponseBody()
            throws JsonProcessingException {
        // given
        final BidderCall<AdQueryRequest> httpCall = givenHttpCall(identity());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().id("abs1").build());

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .hasSize(1)
                .containsExactly("UAH");
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .hasSize(1)
                .containsExactly(BidType.banner);
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(BidderBid::getBid)
                .containsExactly(Bid.builder()
                        .id("abs")
                        .impid("1")
                        .price(BigDecimal.valueOf(321))
                        .adm("<script src=\"AnyLib\"></script>AnyTag")
                        .adomain(singletonList("any"))
                        .crid("312")
                        .w(120)
                        .h(320)
                        .build());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldCorrectPopulateCurrencyWhenCurrencyInResponseSpecified()
            throws JsonProcessingException {
        // given
        final BidderCall<AdQueryRequest> httpCall = givenHttpCall(identity());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().id("abs1").build());

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("UAH");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldUseDefaultCurrencyWhenCurrencyInResponseDoesNotSpecified()
            throws JsonProcessingException {
        // given
        final BidderCall<AdQueryRequest> httpCall = givenHttpCall(
                adQueryDataResponse -> adQueryDataResponse.currency(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().id("abs1").build());

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("PLN");
        assertThat(result.getErrors()).isEmpty();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
                                              Imp... imps) {
        return requestCustomizer.apply(BidRequest.builder()
                .id("22e26bd9a702bc")
                .imp(List.of(imps))
                .user(User.builder().id("user-id").build())
                .device(Device.builder()
                        .ip("31.21.32.42")
                        .ipv6("131.123.632.32")
                        .ua("ua-test-value")
                        .build())
                .site(Site.builder().page("https://www.unknowsite.com")
                        .build())).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("1")
                        .tagid("test-banner-imp-id")
                        .banner(Banner.builder()
                                .format(List.of(
                                        Format.builder().w(320).h(100).build(),
                                        Format.builder().w(300).h(250).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdQuery.of("pl1", "6d93f2a0e5f0fe2cc3a6e9e3ade964b43b07f897")))))
                .build();
    }

    private static BidderCall<AdQueryRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(200, null, body), null);
    }

    private static BidderCall<AdQueryRequest> givenHttpCall(
            UnaryOperator<AdQueryDataResponse.AdQueryDataResponseBuilder> adQueryResponseBuilder)
            throws JsonProcessingException {

        final AdQueryDataResponse adQueryResponse = adQueryResponseBuilder.apply(AdQueryDataResponse.builder()
                        .requestId("abs")
                        .cpm(BigDecimal.valueOf(321))
                        .creationId("312")
                        .adqLib("AnyLib")
                        .currency("UAH")
                        .tag("AnyTag")
                        .adDomains(singletonList("any"))
                        .adQueryMediaType(AdQueryMediaType.of(BidType.banner, 120, 320)))
                .build();
        final String body = mapper.writeValueAsString(AdQueryResponse.of(adQueryResponse));
        return BidderCall.succeededHttp(null, HttpResponse.of(200, null, body), null);
    }
}
