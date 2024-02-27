package org.prebid.server.bidder.mabidder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import lombok.Value;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.mabidder.response.MabidderBidResponse;
import org.prebid.server.bidder.mabidder.response.MabidderResponse;
import org.prebid.server.bidder.mabidder.response.Meta;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class MabidderBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com";

    private final MabidderBidder target = new MabidderBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void shouldFailOnBidderCreation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MabidderBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri()).isEqualTo(ENDPOINT_URL));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBody() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody()).isEqualTo(jacksonMapper.encodeToBytes(bidRequest)))
                .satisfies(request -> assertThat(request.getPayload()).isEqualTo(bidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).flatExtracting(HttpRequest::getImpIds).containsExactly("imp_id");
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(BidderError.badServerResponse("Bad Server Response"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedBid = Bid.builder()
                .adomain(List.of("domain1", "domain2"))
                .crid("crid")
                .h(200)
                .w(100)
                .id("requestId")
                .impid("requestId")
                .adm("<ad>")
                .dealid("dealId")
                .price(BigDecimal.TEN)
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, banner, "USD"));

    }

    private static String givenBidResponse() throws JsonProcessingException {
        return mapper.writeValueAsString(MabidderResponse.of(List.of(
                MabidderBidResponse.builder()
                        .currency("USD")
                        .ad("<ad>")
                        .cpm(BigDecimal.TEN)
                        .requestId("requestId")
                        .width(100)
                        .height(200)
                        .creativeId("crid")
                        .dealId("dealId")
                        .meta(Meta.of(List.of("domain1", "domain2")))
                        .build())));
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest() {
        final Imp imp = Imp.builder()
                .id("imp_id")
                .ext(mapper.valueToTree(ExtPrebid.of(null, MabidderImpExt.of("ppid"))))
                .build();
        return BidRequest.builder().imp(Collections.singletonList(imp)).build();
    }

    @Value(staticConstructor = "of")
    private static class MabidderImpExt {
        String ppid;
    }

}
