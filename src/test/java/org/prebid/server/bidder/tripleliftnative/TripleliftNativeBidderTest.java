package org.prebid.server.bidder.tripleliftnative;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.triplelift.ExtImpTriplelift;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class TripleliftNativeBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private TripleliftNativeBidder target = new TripleliftNativeBidder(
            ENDPOINT_URL,
            List.of("foo", "bar", "foobar"),
            jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TripleliftNativeBidder("invalid_url", emptyList(), jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenNoNativeTypeImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .extracting(BidderError::getMessage)
                .containsOnly("Unsupported publisher for triplelift_native", "no native object specified");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsWhenSitePublisherIdIsNotInWhitelist() {
        // given
        target = new TripleliftNativeBidder(
                ENDPOINT_URL, emptyList(), jacksonMapper);

        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().publisher(Publisher.builder().id("test").build()).build()),
                impBuilder -> impBuilder.xNative(Native.builder().build()),
                ExtImpTriplelift.of("inventoryCode", new BigDecimal(23)),
                null);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage())
                .startsWith("Unsupported publisher for triplelift_native");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsWhenAppPublisherIdIsNotInWhitelist() {
        // given
        target = new TripleliftNativeBidder(
                ENDPOINT_URL, emptyList(), jacksonMapper);

        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder().publisher(Publisher.builder().id("test").build()).build()),
                impBuilder -> impBuilder.xNative(Native.builder().build()),
                ExtImpTriplelift.of("inventoryCode", new BigDecimal(23)),
                null);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage())
                .startsWith("Unsupported publisher for triplelift_native");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsWhenNoPublisherIdIsSpecified() {
        // given
        target = new TripleliftNativeBidder(
                ENDPOINT_URL, emptyList(), jacksonMapper);

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.xNative(Native.builder().build()),
                ExtImpTriplelift.of("inventoryCode", new BigDecimal(23)),
                null);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage())
                .startsWith("Unsupported publisher for triplelift_native");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().publisher(Publisher.builder().id("foo").build()).build())
                .imp(singletonList(Imp.builder()
                        .xNative(Native.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getErrors().get(1).getMessage()).startsWith("No valid impressions for triplelift");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldModifyBidFloorImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().publisher(Publisher.builder().id("foo").build()).build())
                        .id("request_id"),
                impBuilder -> impBuilder.xNative(Native.builder().build())
                        .tagid("willChange")
                        .bidfloor(new BigDecimal(2)),
                ExtImpTriplelift.of("inventoryCode", new BigDecimal(23)),
                null);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsOnly(new BigDecimal(23));
    }

    @Test
    public void makeHttpRequestsShouldResolveTagIdFromInventoryCodeWhenTagCodeIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().publisher(Publisher.builder().id("foo").domain("msn.com").build()).build())
                        .id("request_id"),
                impBuilder -> impBuilder.xNative(Native.builder().build())
                        .tagid("willChange")
                        .bidfloor(new BigDecimal(2)),
                ExtImpTriplelift.of("inventoryCode", new BigDecimal(23)),
                TripleliftNativeExtImpData.of(""));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("inventoryCode");
    }

    @Test
    public void makeHttpRequestsShouldResolveTagIdFromInvCodeWhenTagCodeIsNotBlankAndSiteAndAppDomainsAreNotMsn() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder()
                                .publisher(Publisher.builder().id("foo").domain("not.msn.com").build())
                                .build())
                        .app(App.builder()
                                .publisher(Publisher.builder().id("foo").domain("not.msn.com").build())
                                .build())
                        .id("request_id"),
                impBuilder -> impBuilder.xNative(Native.builder().build())
                        .tagid("willChange")
                        .bidfloor(new BigDecimal(2)),
                ExtImpTriplelift.of("inventoryCode", new BigDecimal(23)),
                TripleliftNativeExtImpData.of("tagCode"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("inventoryCode");
    }

    @Test
    public void makeHttpRequestsShouldResolveTagIdFromDataTagCodeWhenTagCodeIsNotBlankAndSiteDomainIsMsn() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().publisher(Publisher.builder().id("foo").domain("msn.com").build()).build())
                        .id("request_id"),
                impBuilder -> impBuilder.xNative(Native.builder().build())
                        .tagid("willChange")
                        .bidfloor(new BigDecimal(2)),
                ExtImpTriplelift.of("inventoryCode", new BigDecimal(23)),
                TripleliftNativeExtImpData.of("tagCode"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("tagCode");
    }

    @Test
    public void makeHttpRequestsShouldResolveTagIdFromDataTagCodeWhenTagCodeIsNotBlankAndAppDomainIsMsn() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder().publisher(Publisher.builder().id("foo").domain("msn.com").build()).build())
                        .id("request_id"),
                impBuilder -> impBuilder.xNative(Native.builder().build())
                        .tagid("willChange")
                        .bidfloor(new BigDecimal(2)),
                ExtImpTriplelift.of("inventoryCode", new BigDecimal(23)),
                TripleliftNativeExtImpData.of("tagCode"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("tagCode");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage())
                .startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().getFirst().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnXnativeBidTypeType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer,
            ExtImpTriplelift extImpBidder,
            TripleliftNativeExtImpData extImpData) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer, extImpBidder, extImpData))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                              ExtImpTriplelift extImpBidder,
                                              TripleliftNativeExtImpData extImpData) {

        return givenBidRequest(identity(), impCustomizer, extImpBidder, extImpData);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                ExtImpTriplelift bidder,
                                TripleliftNativeExtImpData data) {

        return impCustomizer.apply(Imp.builder()
                        .ext(mapper.valueToTree(TripleliftNativeExtImp.of(bidder, data))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
