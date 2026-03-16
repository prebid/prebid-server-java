package org.prebid.server.bidder.trustx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtBidBidderTrustx;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtBidTrustx;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtImpTrustx;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtImpTrustxData;
import org.prebid.server.proto.openrtb.ext.request.trustx.ExtImpTrustxDataAdServer;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class TrustxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private final TrustxBidder target = new TrustxBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TrustxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getMethod)
                .containsExactly(HttpMethod.POST);
    }

    @Test
    public void makeHttpRequestsShouldReturnASingleRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", givenImpExt()),
                givenImp("imp2", givenImpExt()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedDefaultHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.6"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWhenSiteIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder()
                        .ref("https://referer.com")
                        .domain("example.com")
                        .build()),
                givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.REFERER_HEADER.toString(), "https://referer.com"),
                        tuple(HttpUtil.ORIGIN_HEADER.toString(), "example.com"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWhenDeviceIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder()
                        .ip("192.168.1.1")
                        .ua("Mozilla/5.0")
                        .build()),
                givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "192.168.1.1"),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "Mozilla/5.0"));
    }

    @Test
    public void makeHttpRequestsShouldPreferIpv6InXForwardedForHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder()
                        .ip("192.168.1.1")
                        .ipv6("2001:db8::1").build()),
                givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        result.getValue().getFirst().getHeaders().getAll(HttpUtil.X_FORWARDED_FOR_HEADER);
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(headers -> headers.getAll(HttpUtil.X_FORWARDED_FOR_HEADER.toString()))
                .containsExactly("2001:db8::1");
    }

    @Test
    public void makeHttpRequestsShouldReturnImpWithUnmodifiedExtWhenExtIsInvalid() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(Map.of("data", "invalid"));
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(invalidExt);
    }

    @Test
    public void makeHttpRequestsShouldReturnImpWithUnmodifiedExtWhenExtIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .hasSize(1)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldSetGpidFromAdSlotWhenAdSlotIsPresent() {
        // given
        final ObjectNode impExt = givenImpExtWithAdSlot("adSlotValue", "gpidValue");
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("gpid").textValue())
                .containsExactly("adSlotValue");
    }

    @Test
    public void makeHttpRequestsShouldPreserveExistingGpidWhenAdSlotIsAbsent() {
        // given
        final ObjectNode impExt = givenImpExtWithAdSlot(null, "existingGpid");
        final BidRequest bidRequest = givenBidRequest(givenImp("imp1", impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("gpid").textValue())
                .containsExactly("existingGpid");
    }

    @Test
    public void makeHttpRequestsShouldHandleMultipleImpressions() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", givenImpExtWithAdSlot("adSlot1", "gpid1")),
                givenImp("imp2", givenImpExtWithAdSlot("adSlot2", "gpid2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2)
                .extracting(imp -> imp.getExt().get("gpid").textValue())
                .containsExactly("adSlot1", "adSlot2");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyIsInvalid() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, "\"Invalid body\""),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to parse response as BidResponse: ");
        });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMTypeIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().id("bidId").mtype(null).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing MType for bid: bidId"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedMType() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().id("bidId").mtype(3).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Unsupported MType: 3"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMTypeIs1() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(1).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenMTypeIs2() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(2).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldSetVideoInfoDurationWhenBidDurIsPositive() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(2).dur(30).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getDuration)
                .containsExactly(30);
    }

    @Test
    public void makeBidsShouldNotSetVideoInfoDurationWhenBidDurIsZero() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(2).dur(0).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getDuration)
                .hasSize(1)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldNotSetVideoInfoDurationWhenBidDurIsNull() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(2).dur(null).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getDuration)
                .hasSize(1)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldSetVideoInfoCategoryWhenCatListIsNotEmpty() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(2).cat(List.of("IAB1")).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getPrimaryCategory)
                .containsExactly("IAB1");
    }

    @Test
    public void makeBidsShouldSetFirstCategoryWhenMultipleCategoriesExist() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(2).cat(List.of("IAB1", "IAB2", "IAB3")).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getPrimaryCategory)
                .containsExactly("IAB1");
    }

    @Test
    public void makeBidsShouldNotSetVideoInfoCategoryWhenCatListIsEmpty() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(2).cat(List.of()).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getPrimaryCategory)
                .hasSize(1)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldNotSetVideoInfoCategoryWhenCatListIsNull() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(2).cat(null).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getPrimaryCategory)
                .hasSize(1)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldNotSetVideoInfoForBannerBid() {
        // given
        final Bid bid = Bid.builder().id("bidId").mtype(1).dur(30).cat(List.of("IAB1")).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .hasSize(1)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldModifyBidExtWithNetworkNameFromTrustxExtension() {
        // given
        final ObjectNode bidExt = givenBidExtWithNetworkName("testNetwork");
        final Bid bid = givenBidWithExt(bidExt);
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.path("prebid").path("meta").path("networkName").textValue())
                .containsExactly("testNetwork");
    }

    @Test
    public void makeBidsShouldNotModifyBidExtWhenNetworkNameIsEmpty() {
        // given
        final ObjectNode bidExt = givenBidExtWithNetworkName("");
        final Bid bid = givenBidWithExt(bidExt);
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.path("prebid").path("meta").has("networkName"))
                .containsExactly(false);
    }

    @Test
    public void makeBidsShouldNotModifyBidExtWhenNetworkNameIsNull() {
        // given
        final ObjectNode bidExt = givenBidExtWithNetworkName(null);
        final Bid bid = givenBidWithExt(bidExt);
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.path("prebid").path("meta").has("networkName"))
                .containsExactly(false);
    }

    @Test
    public void makeBidsShouldNotModifyBidExtWhenBidExtIsInvalid() {
        // given
        final ObjectNode bidExt = mapper.createObjectNode().put("bidder", "invalid");
        final Bid bid = givenBidWithExt(bidExt);
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(bidExt);
    }

    @Test
    public void makeBidsShouldNotModifyBidExtWhenBidExtIsNull() {
        // given
        final Bid bid = givenBidWithExt(null);
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .hasSize(1)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldPreserveExistingPrebidExtWhenModifyingMeta() {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "banner");
        final ObjectNode bidExt = givenBidExtWithNetworkName("testNetwork");
        bidExt.set("prebid", prebidNode);
        final Bid bid = givenBidWithExt(bidExt);
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .hasSize(1)
                .allSatisfy(ext -> {
                    assertThat(ext.path("prebid").path("type").textValue()).isEqualTo("banner");
                    assertThat(ext.path("prebid").path("meta").path("networkName").textValue())
                            .isEqualTo("testNetwork");
                });
    }

    @Test
    public void makeBidsShouldReturnMultipleBidsFromSingleSeatBid() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").mtype(1).build();
        final Bid bid2 = Bid.builder().id("bidId2").mtype(2).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid1, bid2);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("bidId1", "bidId2");
    }

    @Test
    public void makeBidsShouldReturnBidsFromMultipleSeatBids() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").mtype(1).build();
        final Bid bid2 = Bid.builder().id("bidId2").mtype(2).build();
        final BidderCall<BidRequest> httpCall = givenHttpCallWithMultipleSeatBids(
                SeatBid.builder().bid(List.of(bid1)).build(),
                SeatBid.builder().bid(List.of(bid2)).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("bidId1", "bidId2");
    }

    @Test
    public void makeBidsShouldReturnValidBidsAndErrorsWhenMixedInput() {
        // given
        final Bid validBid = Bid.builder().id("validBidId").mtype(1).build();
        final Bid invalidBid = Bid.builder().id("invalidBidId").mtype(null).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(validBid, invalidBid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing MType for bid: invalidBidId"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("validBidId");
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer
                .apply(BidRequest.builder()
                        .id("testBidRequestId")
                        .imp(List.of(imps)))
                .build();
    }

    private static Imp givenImp() {
        return givenImp(givenImpExt());
    }

    private static Imp givenImp(ObjectNode impExt) {
        return givenImp(null, impExt);
    }

    private static Imp givenImp(String impId, ObjectNode impExt) {
        return Imp.builder()
                .id(impId)
                .ext(impExt)
                .build();
    }

    private static ObjectNode givenImpExt() {
        return mapper.valueToTree(ExtImpTrustx.builder().build());
    }

    private static ObjectNode givenImpExtWithAdSlot(String adSlot, String gpid) {
        return mapper.valueToTree(ExtImpTrustx.builder()
                .data(new ExtImpTrustxData(null, new ExtImpTrustxDataAdServer(null, adSlot)))
                .gpid(gpid)
                .build());
    }

    private static Bid givenBidWithExt(ObjectNode bidExt) {
        return Bid.builder().id("bidId").mtype(1).ext(bidExt).build();
    }

    private static ObjectNode givenBidExtWithNetworkName(String networkName) {
        return mapper.valueToTree(Map.of("bidder", ExtBidBidderTrustx.of(ExtBidTrustx.of(networkName))));
    }

    private static BidderCall<BidRequest> givenHttpCall(Bid... bids) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(HttpResponseStatus.OK.code(), null, givenBidResponse(bids)),
                null);
    }

    private static BidderCall<BidRequest> givenHttpCallWithMultipleSeatBids(SeatBid... seatBids) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(HttpResponseStatus.OK.code(), null, givenBidResponseWithSeatBids(seatBids)),
                null);
    }

    private static String givenBidResponse(Bid... bids) {
        try {
            return mapper.writeValueAsString(BidResponse.builder()
                    .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error encoding BidResponse to json: " + e);
        }
    }

    private static String givenBidResponseWithSeatBids(SeatBid... seatBids) {
        try {
            return mapper.writeValueAsString(BidResponse.builder()
                    .seatbid(List.of(seatBids))
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error encoding BidResponse to json: " + e);
        }
    }
}
