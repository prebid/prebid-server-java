package org.prebid.server.bidder.mgid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.mgid.ExtImpMgid;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class MgidBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/mgid-exchange/";

    private MgidBidder mgidBidder;

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    @Before
    public void setUp() {
        mgidBidder = new MgidBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new MgidBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mgidBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtAccIdIsBlank() {
        // given
        final String currency = "GRP";
        final BigDecimal bidFloor = new BigDecimal("10.3");
        final String placementId = "placID";
        final String accId = "";
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of(accId, placementId, currency, currency, bidFloor, bidFloor))))
                        .build()))
                .id("reqID")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mgidBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).isEqualToIgnoringCase("accountId is not set");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetTagidToIncomingRequestWhenImpExtHasBlankPlacementId() {
        // given
        final String impId = "impId";
        final String accId = "accId";
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id(impId)
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of(accId, null, null, null, null, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mgidBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly(impId);
    }

    @Test
    public void makeHttpRequestsShouldSetTagidToIncomingRequestWhenImpExtHasNotBlankPlacementId() {
        // given
        final String impId = "impId";
        final String accId = "accId";
        final String placementId = "placID";
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id(impId)
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of(accId, placementId, null, null, null, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mgidBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedTagId = placementId + "/" + impId;

        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly(expectedTagId);
    }

    @Test
    public void makeHttpRequestsShouldSetBidFloorCurAndBidFloorToIncomingRequestWhenImpExtHasNotBlankCurAndBidfloor()
            throws JsonProcessingException {

        // given
        final String currency = "GRP";
        final BigDecimal bidFloor = new BigDecimal("10.3");
        final String placementId = "placID";
        final String accId = "accId";
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .bidfloor(new BigDecimal(3))
                        .bidfloorcur("be replaced")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of(accId, placementId, currency, null, bidFloor, null))))
                        .build()))
                .id("reqID")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mgidBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedTagId = placementId + "/null";
        final BidRequest expected = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .bidfloor(bidFloor)
                        .bidfloorcur(currency)
                        .tagid(expectedTagId)
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of("accId", placementId, currency, null, bidFloor, null))))
                        .build()))
                .id("reqID")
                .build();

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .containsOnly(mapper.writeValueAsString(expected));
    }

    @Test
    public void makeHttpRequestsShouldSetBidFloorCurAndBidFloorToRequestWhenImpExtHasNotBlankCurrencyAndBidFloor()
            throws JsonProcessingException {
        // given
        final String currency = "GRP";
        final BigDecimal bidFloor = new BigDecimal("10.3");
        final String placementId = "placID";
        final String accId = "accId";
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .bidfloor(new BigDecimal(3))
                        .bidfloorcur("be replaced")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of(accId, placementId, null, currency, null, bidFloor))))
                        .build()))
                .id("reqID")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mgidBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedTagId = placementId + "/null";
        final BidRequest expected = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .bidfloor(bidFloor)
                        .bidfloorcur(currency)
                        .tagid(expectedTagId)
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of("accId", placementId, null, currency, null, bidFloor))))
                        .build()))
                .id("reqID")
                .build();

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .containsOnly(mapper.writeValueAsString(expected));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequestWhenImpExtNotContainsParameters()
            throws JsonProcessingException {
        // given
        final String placementId = "placID";
        final String impId = "impId";
        final String accId = "accId";
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id(impId)
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of(accId, placementId, null, null, null, null))))
                        .build()))
                .tmax(1000L)
                .id("reqID")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mgidBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedTagId = placementId + "/" + impId;
        final BidRequest expected = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id(impId)
                        .tagid(expectedTagId)
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMgid.of("accId", placementId, null, null, null, null))))
                        .build()))
                .tmax(1000L)
                .id("reqID")
                .build();

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .containsOnly(mapper.writeValueAsString(expected));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = mgidBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = mgidBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = mgidBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenBannerIsPresentAndExtCrtypeIsNotSet() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = mgidBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnXNativeBidWhenBidIsPresentAndExtCrtypeIsNative() throws JsonProcessingException {
        // given
        final ObjectNode crtypeNode = mapper.createObjectNode().put("crtype", "native");
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").ext(crtypeNode))));

        // when
        final Result<List<BidderBid>> result = mgidBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(crtypeNode).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenBidIsPresentAndExtCrtypeIsBlank() throws JsonProcessingException {
        // given
        final ObjectNode crtypeNode = mapper.createObjectNode().put("crtype", "");
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").ext(crtypeNode))));

        // when
        final Result<List<BidderBid>> result = mgidBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(crtypeNode).build(), banner, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(mgidBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }
}
