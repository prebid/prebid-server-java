package org.prebid.server.bidder.openweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.openweb.ExtImpOpenweb;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class OpenWebBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com/";

    private OpenWebBidder openWebBidder;

    @Before
    public void setUp() {
        openWebBidder = new OpenWebBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OpenWebBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openWebBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test-url.com/?aid=4");
    }

    @Test
    public void makeHttpRequestsShouldModifyImpExt() {
        // given
        final ExtPrebid<?, ExtImpOpenweb> openwebImpExt = givenOpenWebImpExt(1, 1, 1, BigDecimal.ONE);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mapper.valueToTree(openwebImpExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openWebBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedImpExt = mapper.createObjectNode()
                .set("openweb", mapper.valueToTree(openwebImpExt.getBidder()));
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldModifyImpBidFloorWhenImpExtBidFloorIsValid() {
        // given
        final ExtPrebid<?, ExtImpOpenweb> openwebImpExt = givenOpenWebImpExt(1, 1, 1, BigDecimal.ONE);
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.bidfloor(BigDecimal.valueOf(3L)).ext(mapper.valueToTree(openwebImpExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openWebBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpBidFloorWhenImpExtBidFloorIsInvalid() {
        // given
        final ObjectNode openwebImpExtNode = givenOpenWebImpExtObjectNode(1, 1, 1, BigDecimal.ZERO);
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.bidfloor(BigDecimal.ONE).ext(openwebImpExtNode));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openWebBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void makeHttpRequestsShouldSplitImpsBySourceId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("123")
                        .ext(givenOpenWebImpExtObjectNode(1, 1, 1, BigDecimal.ONE)),
                impBuilder -> impBuilder.id("234")
                        .ext(givenOpenWebImpExtObjectNode(1, 1, 1, BigDecimal.ONE)),
                impBuilder -> impBuilder.id("345")
                        .ext(givenOpenWebImpExtObjectNode(2, 1, 1, BigDecimal.ONE)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openWebBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final List<HttpRequest<BidRequest>> requests = result.getValue();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getPayload().getImp())
                .extracting(Imp::getId)
                .containsExactlyInAnyOrder("123", "234");
        assertThat(requests.get(1).getPayload().getImp())
                .extracting(Imp::getId)
                .containsExactly("345");
    }

    @Test
    public void makePodHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openWebBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(bidderError.getMessage()).startsWith(
                            "ignoring imp id=123, error while encoding impExt, err: Cannot deserialize instance");
                });
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = openWebBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = openWebBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = openWebBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = openWebBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().id("123").impid("123").build(), video, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentAndVideoIsAbsentInRequestImp()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = openWebBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().id("123").impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnErrorIfNativeAndVideoAreAbsentInRequestImp()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.xNative(null).video(null)),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("345"))));

        // when
        final Result<List<BidderBid>> result = openWebBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();

        final String expectedErrorMessage = "ignoring bid id=123, request doesn't contain any impression with id=345";
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse(expectedErrorMessage));
    }

    @Test
    public void makeBidsShouldProceedWithErrorsAndValues() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("123").banner(Banner.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder.id("1").impid("123"),
                                bidBuilder -> bidBuilder.id("2").impid("345"))));

        // when
        final Result<List<BidderBid>> result = openWebBidder.makeBids(httpCall, null);

        // then
        final String expectedErrorMessage = "ignoring bid id=2, request doesn't contain any impression with id=345";
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse(expectedErrorMessage));

        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().id("1").impid("123").build(), banner, null));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<Function<Imp.ImpBuilder, Imp.ImpBuilder>> impCustomizers) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .device(Device.builder().ua("ua").ip("ip").ipv6("ipv6").build())
                        .imp(impCustomizers.stream()
                                .map(OpenWebBidderTest::givenImp)
                                .collect(Collectors.toList())))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder>... impCustomizers) {
        return givenBidRequest(identity(), asList(impCustomizers));
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(
                        Imp.builder()
                                .id("123")
                                .ext(givenOpenWebImpExtObjectNode(4, 5, 6, BigDecimal.ONE)))
                .build();
    }

    private static ExtPrebid<?, ExtImpOpenweb> givenOpenWebImpExt(Integer sourceId,
                                                                  Integer placementId,
                                                                  Integer siteId,
                                                                  BigDecimal bidFloor) {
        return ExtPrebid.of(null, ExtImpOpenweb.of(sourceId, placementId, siteId, bidFloor));
    }

    private static ObjectNode givenOpenWebImpExtObjectNode(Integer sourceId,
                                                           Integer placementId,
                                                           Integer siteId,
                                                           BigDecimal bidFloor) {
        return mapper.valueToTree(givenOpenWebImpExt(sourceId, placementId, siteId, bidFloor));
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .seatbid(singletonList(
                        SeatBid.builder()
                                .bid(Arrays.stream(bidCustomizers)
                                        .map(bidCustomizer -> bidCustomizer.apply(Bid.builder().id("123")).build())
                                        .collect(Collectors.toList()))
                                .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
