package org.prebid.server.bidder.bmtm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.bmtm.ExtImpBmtm;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class BmtmBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://localhost:8080";

    private BmtmBidder bmtmBidder;

    @Before
    public void setUp() {
        bmtmBidder = new BmtmBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BmtmBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBmtm.of("placement_id")))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("someUa").ip("someIp").build())
                .site(Site.builder().page("somePage").build())
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bmtmBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), bidRequest.getDevice().getUa()),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), bidRequest.getDevice().getIp()),
                        tuple(HttpUtil.REFERER_HEADER.toString(), bidRequest.getSite().getPage()));
    }

    @Test
    public void makeHttpRequestsShouldNotAddIpv6IfIpIsPresent() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBmtm.of("placement_id")))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("someUa").ip("someIp").ipv6("ipv6").build())
                .site(Site.builder().page("somePage").build())
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bmtmBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), bidRequest.getDevice().getIp()));
    }

    @Test
    public void makeHttpRequestsShouldAddIpv6IfIpIsNotPresent() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBmtm.of("placement_id")))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("someUa").ipv6("ipv6").build())
                .site(Site.builder().page("somePage").build())
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bmtmBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), bidRequest.getDevice().getIpv6()));
    }

    @Test
    public void makeHttpRequestsShouldAddExceptionIfNoBannerAndVideoProvided() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .banner(null)
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBmtm.of("placement_id")))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("someUa").ipv6("ipv6").build())
                .site(Site.builder().page("somePage").build())
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bmtmBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("For Imp ID 123 Banner or Video is undefined"));
    }

    @Test
    public void makeHttpRequestsShouldModifyTagIdForOutgoingRequest() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBmtm.of("placement_id")))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bmtmBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("placement_id");
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestForEachImp() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBmtm.of("placement_id")))));
        final Imp secondImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBmtm.of("placement_id2")))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bmtmBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("placement_id", "placement_id2");
    }

    @Test
    public void makeHttpRequestsShouldDeleteExtForOutgoingRequestIfRequestIsValid() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBmtm.of("placement_id")))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bmtmBidder.makeHttpRequests(bidRequest);

        // then
        final Imp expectedImp = givenImp(impBuilder -> impBuilder.ext(null).tagid("placement_id"));
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsOnly(expectedImp);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = bmtmBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Missing bidder ext in impression with id: 123");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = bmtmBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = bmtmBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = bmtmBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, null));
    }

    @Test
    public void makeBidsShouldReturnBannerIfBidIdIsNotPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = bmtmBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().w(23).h(25).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBmtm.of("placement_id")))))
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
