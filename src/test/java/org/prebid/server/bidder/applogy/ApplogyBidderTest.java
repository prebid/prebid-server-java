package org.prebid.server.bidder.applogy;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.*;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.*;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.applogy.ExtImpApplogy;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ApplogyBidderTest extends VertxTest {


    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private ApplogyBidder applogyBidder;

    @Before
    public void setUp() {
        applogyBidder = new ApplogyBidder(ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ApplogyBidder("invalid_url"));
    }

    @Test
    public void makeHttpRequestsShouldSkipEmptyTokens() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(identity()),
                        givenImp(impBuilder -> impBuilder.banner(null).xNative(Native.builder().build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = applogyBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "token is empty"));
    }

    @Test
    public void makeHttpRequestsShouldSkipInvalidImpressionAndAddError() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(identity(), "1234"),
                        givenImp(impBuilder -> impBuilder.banner(null).id("2").audio(Audio.builder().build()), "12345")))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = applogyBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "Applogy only supports banner, video and native media types. Ignoring imp id=2"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .containsOnly(givenImp(identity(), "1234"));
    }


    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = applogyBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("failed to decode json");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseWithNoContent() {
        // given
        final HttpCall<BidRequest> httpCall = HttpCall
                .success(null, HttpResponse.of(204, null, null), null);

        // when
        final Result<List<BidderBid>> result = applogyBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }


    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, "");
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer, String token) {
        return impCustomizer.apply(Imp.builder()
                .id("1")
                .banner(Banner.builder().id("banner_id").build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpApplogy.of(token)))))
                .build();
    }


    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

}
