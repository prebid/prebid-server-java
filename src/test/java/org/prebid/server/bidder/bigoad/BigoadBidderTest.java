package org.prebid.server.bidder.bigoad;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bigoad.ExtImpBigoad;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class BigoadBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://domain.com/path?{{SspId}}";

    private final BigoadBidder target = new BigoadBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndPointUrl() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> new BigoadBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorOnInvalidImpExt() {
        // given
        final BidRequest request = givenBidRequest(givenImp(imp -> imp.ext(
                mapper.valueToTree(Map.of("bidder", emptyList())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage())
                    .startsWith("imp null: unable to unmarshal ext.bidder: Cannot deserialize value of type");
        });
    }

    @Test
    public void makeHttpRequestsShouldModifyFirstImp() {
        // given
        final BidRequest request = givenBidRequest(
                givenImp(ExtImpBigoad.of("sspId0")),
                givenImp(ExtImpBigoad.of("sspId1")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(
                        givenImp(imp -> imp.ext(mapper.valueToTree(ExtImpBigoad.of("sspId0")))),
                        givenImp(ExtImpBigoad.of("sspId1")));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnOpenRTBHeader() {
        // given
        final BidRequest request = givenBidRequest(givenImp(ExtImpBigoad.of("sspId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedUri() {
        // given
        final BidRequest request = givenBidRequest(
                givenImp(ExtImpBigoad.of("sspId0")),
                givenImp(ExtImpBigoad.of("sspId1")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://domain.com/path?sspId0");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Bad server response: Failed to decode: Unrecognized token");
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
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnErrorOnAudio() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(givenBid(bid -> bid.mtype(3))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("unrecognized bid type in response from bigoad null"));
    }

    @Test
    public void makeBidsShouldReturnErrorOnInvalidMtype() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(givenBid(bid -> bid.mtype(0))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("unrecognized bid type in response from bigoad null"));
    }

    @Test
    public void makeBidsShouldReturnExpectedResult() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(
                givenBid(bid -> bid.mtype(1)),
                givenBid(bid -> bid.mtype(2)),
                givenBid(bid -> bid.mtype(4))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(banner, video, xNative);
        assertThat(result.getErrors()).isEmpty();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder().imp(asList(imps)).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static Imp givenImp(ExtImpBigoad extImpBigoad) {
        return givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, extImpBigoad))));
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(asList(bids)).build()))
                .build());
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body), null);
    }
}
