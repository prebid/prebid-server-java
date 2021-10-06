package org.prebid.server.bidder.smarthub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
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
import org.prebid.server.proto.openrtb.ext.request.adocean.ExtImpAdocean;
import org.prebid.server.proto.openrtb.ext.request.smarthub.ExtImpSmarthub;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class SmarthubBidderTest extends VertxTest {

    private static final String ENDPOINT_URL =
            "http://localhost/prebid_server?host={{Host}}&AccountID={{AccountID}}&SourceId={{SourceId}}";

    private SmarthubBidder smarthubBidder;

    @Before
    public void setUp() {
        smarthubBidder = new SmarthubBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SmarthubBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddAllHeaders() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smarthubBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple("Prebid-Adapter-Ver", "1.0.0"));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyReplaceFieldsInUrl() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpSmarthub.of("somePartnerName", "someSeat", "someToken")))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smarthubBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        "http://localhost/prebid_server?host=somePartnerName&AccountID=someSeat&SourceId=someToken");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = smarthubBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyValuesAndErrorIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = smarthubBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("SeatBid[0].Bid[0] cannot be empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyValuesListAndErrorIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(null).build()));

        // when
        final Result<List<BidderBid>> result = smarthubBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("SeatBid[0].Bid[0] cannot be empty");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldProccedWithErrorsAndValues() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(
                        builder -> builder
                                .ext(mapper.createObjectNode()
                                        .set("mediaType", TextNode.valueOf(BidType.video.toString()))))));

        // when
        final Result<List<BidderBid>> result = smarthubBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(value -> assertThat(value.getType()).isEqualTo(BidType.video));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidDoesNotContainExt() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(builder -> builder.ext(null))));

        // when
        final Result<List<BidderBid>> result = smarthubBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("missing bid ext");
                });
    }

    @Test
    public void makeBidsShouldReturnErrorIfExtIncorrect() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(builder -> builder.ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpAdocean.of("someEmitterDomain", "someMasterId", "someSlaveID")))))));

        // when
        final Result<List<BidderBid>> result = smarthubBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("missing bid ext");
                });
    }

    @Test
    public void makeBidsShouldReturnErrorIfSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(Collections.emptyList())
                        .build()));

        // when
        final Result<List<BidderBid>> result = smarthubBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("SeatBid[0].Bid[0] cannot be empty");
                });
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("someId")
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpSmarthub.of("somePartnerName", "someSeat", "someToken")))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    @SafeVarargs
    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(Arrays.stream(bidCustomizers)
                                .map(bidCustomizer -> bidCustomizer.apply(Bid.builder()).build())
                                .collect(Collectors.toList()))
                        .build()))
                .ext(ExtBidResponse.builder().build())
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }
}
