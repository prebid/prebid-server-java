package org.prebid.server.bidder.iqx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.iqx.ExtImpIqx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

public class IqxBidderTest extends VertxTest {

    private final IqxBidder target = new IqxBidder("https://test.dm/{{SourceId}}/{{Host}}", jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> new IqxBidder("incorrect_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenInvalidImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage()).startsWith("Failed to deserialize IQZonex extension:");
        });
    }

    @Test
    public void makeHttpRequestsShouldResolveEndpointUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(ExtImpIqx.of("env", "pid")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.dm/pid/env");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestForEachValidImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(ExtImpIqx.of("env0", "pid0")),
                givenInvalidImp(),
                givenImp(ExtImpIqx.of("env2", "pid2")),
                givenImp(ExtImpIqx.of(null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(3)
                .extracting(HttpRequest::getUri, httpRequest -> httpRequest.getPayload().getImp().size())
                .containsExactly(
                        tuple("https://test.dm/pid0/env0", 1),
                        tuple("https://test.dm/pid2/env2", 1),
                        tuple("https://test.dm//", 1));
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
        });
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Array SeatBid cannot be empty"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Array SeatBid cannot be empty"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Array SeatBid cannot be empty"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfMtypeInvalid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(
                givenBidResponse(givenBid(0))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("failed to parse bid mtype for impression id \"null\""));
    }

    @Test
    public void makeBidsShouldReturnBidsWithProperType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(
                givenBidResponse(
                        givenBid(null),
                        givenBid(1),
                        givenBid(2),
                        givenBid(3),
                        givenBid(4))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(3)
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner, BidType.video, BidType.xNative);
        assertThat(result.getErrors()).hasSize(2)
                .containsOnly(BidderError.badServerResponse("failed to parse bid mtype for impression id \"null\""));
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()).build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(request -> request.imp(List.of(imps)));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static Imp givenImp(ExtImpIqx extImpIqx) {
        return givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, extImpIqx))));
    }

    private static Imp givenInvalidImp() {
        return givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
    }

    private BidderCall<BidRequest> givenHttpCall(String body) {
        final HttpResponse response = HttpResponse.of(200, null, body);
        return BidderCall.succeededHttp(null, response, null);
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build();
    }

    private static Bid givenBid(Integer mtype) {
        return Bid.builder().mtype(mtype).build();
    }
}
