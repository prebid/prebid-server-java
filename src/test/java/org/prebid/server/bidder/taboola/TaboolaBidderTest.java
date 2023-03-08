package org.prebid.server.bidder.taboola;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.taboola.ExtImpTaboola;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import io.vertx.core.MultiMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class TaboolaBidderTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private TaboolaBidder taboolaBidder;

    @Before
    public void setUp() {
        taboolaBidder =
                new TaboolaBidder("https://{{MediaType}}.bidder.taboola.com/OpenRTB/PS/auction/{{Host}}/{{PublisherID}}", jacksonMapper);
    }

    @Test
    public void createBidderWithWrongEndpointShouldThrowException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TaboolaBidder("incorrect.endpoint",
                jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldTakeCurrencyAmountFromImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer.banner(Banner.builder().build()).bidfloorcur("USD"));

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestWithEmptySiteShouldTakeDataForSiteFromImpValues() {
        // given
        final Imp impBanner = givenImp(impCustomizer ->
                impCustomizer.banner(Banner.builder().build()).bidfloorcur("USD")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpTaboola.of("token",
                                "test.com",
                                "1",
                                BigDecimal.TEN,
                                List.of("test-cat"),
                                List.of("test-badv"),
                                "test",
                                1)))));

        final BidRequest bidRequest = BidRequest
                .builder().imp(List.of(impBanner)).site(null).build();

        final Site expectedSite = Site.builder()
                .id("token")
                .name("token")
                .domain("test.com")
                .publisher(Publisher.builder().id("token").build())
                .build();

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .containsExactly(expectedSite);
    }

    @Test
    public void makeHttpRequestShouldSplitInTwoSeparateRequests() {
        // given
        final Imp impBanner = givenImp(impCustomizer ->
                impCustomizer.banner(Banner.builder().build()).bidfloorcur("USD"));
        final Imp impNative = givenImp(impCustomizer ->
                impCustomizer.xNative(Native.builder().build()).bidfloorcur("EUR"));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(impBanner, impNative)).build();

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, "USD"), tuple(BigDecimal.TEN, "EUR"));
    }

    @Test
    public void makeHttpRequestShouldContainProperUriAndHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer.banner(Banner.builder().build()).bidfloorcur("USD"));

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .contains("https://display.bidder.taboola.com/OpenRTB/PS/auction/test.com/token")
                .doesNotContain(BidType.banner.getName());
    }

    @Test
    public void makeHttpRequestWithInvalidTypeShouldReturnErrorWithProperMessage() {
        final BidRequest bidRequest = givenBidRequest(
                impCustomizer -> impCustomizer.video(Video.builder().build()).bidfloorcur("USD"));

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("For Imp ID 123 Banner or Native is undefined");
    }

    @Test
    public void makeBidsWithInvalidBodyShouldResultInError() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = taboolaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
    }

    @Test
    public void makeBidsReturnEmptyListsResultWhenEmptySeatBidInBidResponse() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = taboolaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnValidBidResponseWithVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer.xNative(Native.builder().build()));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = taboolaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsOnly(Bid.builder()
                        .impid("123")
                        .build());
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpTaboola.of("token",
                                        "test.com",
                                        "1",
                                        BigDecimal.TEN,
                                        null,
                                        null,
                                        null,
                                        null)))))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(List.of(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(bidCustomizer.apply(Bid.builder().impid("123")).build()))
                        .build()))
                .build();
    }
}
