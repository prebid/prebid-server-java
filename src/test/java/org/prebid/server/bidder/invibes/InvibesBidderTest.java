package org.prebid.server.bidder.invibes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.invibes.model.InvibesBidParams;
import org.prebid.server.bidder.invibes.model.InvibesBidRequest;
import org.prebid.server.bidder.invibes.model.InvibesBidderResponse;
import org.prebid.server.bidder.invibes.model.InvibesPlacementProperty;
import org.prebid.server.bidder.invibes.model.InvibesTypedBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.invibes.ExtImpInvibes;
import org.prebid.server.proto.openrtb.ext.request.invibes.model.InvibesDebug;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class InvibesBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{Host}}/test";

    private InvibesBidder invibesBidder;

    @Before
    public void setUp() {
        invibesBidder = new InvibesBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() ->
                new InvibesBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().page("www.test.com").build()),
                impBuilder -> impBuilder.banner(Banner.builder().h(12).w(15).build()),
                ExtImpInvibes.of("12", 1003, InvibesDebug.of("test", true)));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://bid3.videostep.com/test");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Error parsing invibesExt parameters");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().page("www.awesome-page.com").build()),
                impBuilder -> impBuilder.banner(null));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result =
                invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Banner not specified"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSiteIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder.banner(Banner.builder().h(12).w(15).build()));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Site not specified"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void shouldCreateRequestWithDataFromEveryImpression() throws JsonProcessingException {
        // given
        final List<Imp> imps = Arrays.asList(givenImp(
                impBuilder -> impBuilder
                        .banner(Banner.builder().h(10).w(11).build()), ExtImpInvibes.of("12", 15,
                        InvibesDebug.of("test1", true))),
                givenImp(impBuilder -> impBuilder
                        .banner(Banner.builder().h(14).w(15).build()), ExtImpInvibes.of("15", 1001,
                        InvibesDebug.of("test2", false)))
        );
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().page("www.awesome-page.com").build())
                .imp(imps)
                .build();
        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload).hasSize(1);
        InvibesBidParams expectedBidParams =
                mapper.readValue(result.getValue().get(0).getPayload().getBidParamsJson(), InvibesBidParams.class);
        assertThat(expectedBidParams.getPlacementIds()).hasSize(2)
                .containsOnly("12", "15");
        final Format firstExpectedFormat = Format.builder().w(11).h(10).build();
        final Format secondExpectedFormat = Format.builder().w(15).h(14).build();
        assertThat(expectedBidParams.getProperties().values()).hasSize(2)
                .containsOnly(InvibesPlacementProperty.builder()
                                .formats(Collections.singletonList(firstExpectedFormat))
                                .build(),
                        InvibesPlacementProperty.builder()
                                .formats(Collections.singletonList(secondExpectedFormat))
                                .build());
    }

    @Test
    public void makeHttpRequestsShouldCreateInvibesBidRequestWithCorrectParams() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .device(Device.builder().w(77).h(88).build())
                        .user(User.builder().buyeruid("someUid").build())
                        .site(Site.builder().page("www.awesome-page.com").build()),
                impBuilder -> impBuilder.banner(Banner.builder().h(12).w(15).build()));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        final Map<String, InvibesPlacementProperty> properties = new HashMap<>();
        properties.put("12", InvibesPlacementProperty.builder()
                .formats(Collections.singletonList(Format.builder().w(15).h(12).build())).build());

        final InvibesBidParams invibesBidParams = InvibesBidParams.builder()
                .placementIds(Collections.singletonList("12"))
                .bidVersion("4")
                .properties(properties)
                .build();

        final InvibesBidRequest expectedRequest = InvibesBidRequest.builder()
                .bidParamsJson(mapper.writeValueAsString(invibesBidParams))
                .isTestBid(true)
                .location("www.awesome-page.com")
                .gdpr(true)
                .gdprConsent(StringUtils.EMPTY)
                .invibBVLog(true)
                .videoAdDebug(true)
                .lid("someUid")
                .bvid("test")
                .width("77")
                .height("88")
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsOnly(expectedRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<InvibesBidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = invibesBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<InvibesBidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = invibesBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final HttpCall<InvibesBidRequest> httpCall = givenHttpCall(
                InvibesBidRequest.builder().build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = invibesBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorIdBidResponseContainsError() throws JsonProcessingException {
        // given
        final HttpCall<InvibesBidRequest> httpCall = givenHttpCall(
                InvibesBidRequest.builder().build(),
                mapper.writeValueAsString(InvibesBidderResponse.builder().error("someError").build()));

        // when
        final Result<List<BidderBid>> result = invibesBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsOnly("Server error: someError.");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(invibesBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static InvibesBidderResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return InvibesBidderResponse.builder()
                .typedBids(singletonList(InvibesTypedBid.builder()
                        .bid(bidCustomizer.apply(Bid.builder()).build())
                        .dealPriority(12)
                        .build()))
                .currency("EUR")
                .build();
    }

    private static HttpCall<InvibesBidRequest> givenHttpCall(InvibesBidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<InvibesBidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            ExtImpInvibes extImpInvibes) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extImpInvibes))))
                .build();
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(
                        givenImp(impCustomizer, ExtImpInvibes.of("12", 15,
                                InvibesDebug.of("test", true))))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                ExtImpInvibes extImpInvibes) {
        return impCustomizer.apply(Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, extImpInvibes))))
                .build();
    }
}
