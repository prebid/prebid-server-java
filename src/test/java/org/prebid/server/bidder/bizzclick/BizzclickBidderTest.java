package org.prebid.server.bidder.bizzclick;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bizzclick.ExtImpBizzclick;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

public class BizzclickBidderTest extends VertxTest {

    private static final String ENDPOINT = "https://{{Host}}/uri?source={{SourceId}}&account={{AccountID}}";
    private static final String DEFAULT_HOST = "host";
    private static final String DEFAULT_ACCOUNT_ID = "accountId";
    private static final String DEFAULT_SOURCE_ID = "sourceId";
    private static final String DEFAULT_PLACEMENT_ID = "placementId";

    private final BizzclickBidder target = new BizzclickBidder(ENDPOINT, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BizzclickBidder("incorrect_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("ext.bidder not provided");
    }

    @Test
    public void makeHttpRequestsShouldRelyOnlyOnFirstImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(),
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldRemoveExtFromEachImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("host", "accountId1", "placementId1", "sourceId1"),
                givenImp("host", "accountId2", "placementId2", "sourceId2"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnlyNulls();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithXOpenRtbVersionHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnlyOnce(tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithUserAgentHeaderIfDeviceUaPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                device -> device.ua("ua"),
                givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnlyOnce(tuple(HttpUtil.USER_AGENT_HEADER.toString(), "ua"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithXForwardedForHeaderIfDeviceIpV6Present() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                device -> device.ipv6("ipv6"),
                givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnlyOnce(tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "ipv6"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithXForwardedForHeaderIfDeviceIpPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                device -> device.ip("ip"),
                givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnlyOnce(tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "ip"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithXForwardedForHeaderWithDeviceIpPrioritizedOverDeviceIpV6() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                device -> device.ip("ip").ipv6("ipv6"),
                givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnlyOnce(tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "ip"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequestWithExpectedUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        String.format("https://%s/uri?source=%s&account=%s",
                                DEFAULT_HOST,
                                DEFAULT_SOURCE_ID,
                                DEFAULT_ACCOUNT_ID));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequestWithExpectedAlternativeUri() {
        // given
        final String expectedDefaultHost = "us-e-node1";
        final BidRequest bidRequest = givenBidRequest(
                givenImp(expectedDefaultHost, DEFAULT_ACCOUNT_ID, DEFAULT_PLACEMENT_ID, null)
        );

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        String.format("https://%s/uri?source=%s&account=%s",
                                expectedDefaultHost,
                                DEFAULT_PLACEMENT_ID,
                                DEFAULT_ACCOUNT_ID));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("Incorrect body", null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Bad server response."));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("null", null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(identity()), null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(response -> response.seatbid(emptyList())), null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldRelyOnlyOnFirstSeatBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(response -> response.seatbid(List.of(
                        SeatBid.builder().bid(singletonList(Bid.builder().id("1").build())).build(),
                        SeatBid.builder().bid(singletonList(Bid.builder().id("2").build())).build()))),
                givenBidRequest(givenImp(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("1");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseFirstSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(response -> response.seatbid(singletonList(null))), null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseFirstSeatBidBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(response -> response.seatbid(singletonList(SeatBid.builder().build()))), null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsWithBannerMediaTypeByDefault() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(response -> response.seatbid(singletonList(
                        SeatBid.builder().bid(singletonList(Bid.builder().impid("1").build())).build()))),
                givenBidRequest(givenImp(imp -> imp.id("1"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsWithVideoMediaTypeIfImpVideoPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(response -> response.seatbid(singletonList(
                        SeatBid.builder().bid(List.of(Bid.builder().impid("1").build())).build()))),
                givenBidRequest(givenImp(imp -> imp.id("1").video(Video.builder().build()))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsWithXNativeMediaTypeIfImpVideoAbsentAndImpXNativePresent()
            throws JsonProcessingException {

        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(response -> response.seatbid(singletonList(
                        SeatBid.builder().bid(List.of(Bid.builder().impid("1").build())).build()))),
                givenBidRequest(givenImp(imp -> imp.id("1").xNative(Native.builder().build()))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsWithDefaultCurrency() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(response -> response.seatbid(singletonList(
                        SeatBid.builder().bid(singletonList(Bid.builder().build())).build()))),
                givenBidRequest(givenImp(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("USD");
        assertThat(result.getErrors()).isEmpty();
    }

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()).build();
    }

    private BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(request -> request.imp(List.of(imps)));
    }

    private BidRequest givenBidRequest(UnaryOperator<Device.DeviceBuilder> deviceCustomizer, Imp... imps) {
        return givenBidRequest(request -> request
                .device(deviceCustomizer.apply(Device.builder()).build())
                .imp(List.of(imps)));
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private Imp givenImp() {
        final ExtPrebid<?, ?> ext = ExtPrebid.of(null, ExtImpBizzclick.of(
                DEFAULT_HOST, DEFAULT_ACCOUNT_ID, DEFAULT_PLACEMENT_ID, DEFAULT_SOURCE_ID
        ));
        return givenImp(imp -> imp.ext(mapper.valueToTree(ext)));
    }

    private Imp givenImp(String host, String accountId, String placementId, String sourceId) {
        final ExtPrebid<?, ?> ext = ExtPrebid.of(
                null, ExtImpBizzclick.of(host, accountId, placementId, sourceId)
        );
        return givenImp(imp -> imp.ext(mapper.valueToTree(ext)));
    }

    private BidderCall<BidRequest> givenHttpCall(String body, BidRequest bidRequest) {
        final HttpRequest<BidRequest> request = HttpRequest.<BidRequest>builder().payload(bidRequest).build();
        final HttpResponse response = HttpResponse.of(200, null, body);
        return BidderCall.succeededHttp(request, response, null);
    }

    private String givenBidResponse(UnaryOperator<BidResponse.BidResponseBuilder> responseCustomizer)
            throws JsonProcessingException {

        final BidResponse response = responseCustomizer.apply(BidResponse.builder()).build();
        return mapper.writeValueAsString(response);
    }
}
