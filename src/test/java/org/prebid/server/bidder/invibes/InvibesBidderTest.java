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
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAmp;
import org.prebid.server.proto.openrtb.ext.request.invibes.ExtImpInvibes;
import org.prebid.server.proto.openrtb.ext.request.invibes.model.InvibesDebug;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class InvibesBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{ZoneID}}.videostep.com/bid/";
    private static final int BANNER_H = 12;
    private static final int BANNER_W = 15;
    private static final String PAGE_URL = "www.test.com";
    private static final int SECOND_BANNER_H = 23;
    private static final int SECOND_BANNER_W = 24;
    private static final String FIRST_PLACEMENT_ID = "12";
    private static final String SECOND_PLACEMENT_ID = "15";
    private static final int DEVICE_W = 77;
    private static final int DEVICE_H = 88;
    private static final String BUYER_UID = "someUid";
    private static final String IMP_ID = "123";
    private static final String CURRENCY = "EUR";
    private static final String BID_VERSION = "4";

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
    public void makeHttpRequestsShouldCreateCorrectURLFor1003Zone() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()),
                ExtImpInvibes.of("12", 1003, InvibesDebug.of("test", true)));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://bid3.videostep.com/bid/");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURLFor0Zone() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()),
                ExtImpInvibes.of("12", 0, InvibesDebug.of("test", true)));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://bid.videostep.com/bid/");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURLFor1Zone() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()),
                ExtImpInvibes.of("12", 1, InvibesDebug.of("test", true)));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://bid.videostep.com/bid/");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURLFor1001Zone() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()),
                ExtImpInvibes.of("12", 1001, InvibesDebug.of("test", true)));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://bid.videostep.com/bid/");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURLFor999Zone() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()),
                ExtImpInvibes.of("12", 999, InvibesDebug.of("test", true)));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://bid999.videostep.com/bid/");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().page(PAGE_URL).build())
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
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().page(PAGE_URL).build()),
                impBuilder -> impBuilder.id(IMP_ID).banner(null));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result =
                invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        BidderError.badInput("Banner not specified in impression with id: " + IMP_ID));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSiteIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder.banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Site not specified"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void shouldCreateRequestWithDataFromEveryImpression() {
        // given
        final List<Imp> imps = Arrays.asList(givenImp(
                        impBuilder -> impBuilder
                                .banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()),
                        ExtImpInvibes.of(FIRST_PLACEMENT_ID, 15, InvibesDebug.of("test1", true))),
                givenImp(impBuilder -> impBuilder
                                .banner(Banner.builder().h(SECOND_BANNER_H).w(SECOND_BANNER_W).build()),
                        ExtImpInvibes.of(SECOND_PLACEMENT_ID, 1001, InvibesDebug.of("test2", false))));
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().page(PAGE_URL).build())
                .imp(imps)
                .build();

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Format firstExpectedFormat = Format.builder().w(BANNER_W).h(BANNER_H).build();
        final Format secondExpectedFormat = Format.builder().w(SECOND_BANNER_W).h(SECOND_BANNER_H).build();
        final Map<String, InvibesPlacementProperty> bidProperties = new HashMap<>();
        bidProperties.put(FIRST_PLACEMENT_ID, InvibesPlacementProperty.builder()
                .formats(Collections.singletonList(firstExpectedFormat))
                .build());
        bidProperties.put(SECOND_PLACEMENT_ID, InvibesPlacementProperty.builder()
                .formats(Collections.singletonList(secondExpectedFormat))
                .build());

        InvibesBidParams expectedBidParams = InvibesBidParams.builder()
                .placementIds(Arrays.asList(FIRST_PLACEMENT_ID, SECOND_PLACEMENT_ID))
                .bidVersion(BID_VERSION)
                .properties(bidProperties)
                .build();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(invibesBidRequest ->
                        mapper.readValue(invibesBidRequest.getBidParamsJson(), InvibesBidParams.class))
                .containsOnly(expectedBidParams);
    }

    @Test
    public void makeHttpRequestsShouldCreateInvibesBidRequestWithCorrectParams() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .device(Device.builder().w(DEVICE_W).h(DEVICE_H).build())
                        .user(User.builder().buyeruid(BUYER_UID).build())
                        .site(Site.builder().page(PAGE_URL).build()),
                impBuilder -> impBuilder.banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        final Map<String, InvibesPlacementProperty> properties = new HashMap<>();
        properties.put(FIRST_PLACEMENT_ID, InvibesPlacementProperty.builder()
                .formats(Collections.singletonList(Format.builder().w(BANNER_W).h(BANNER_H).build())).build());

        final InvibesBidParams invibesBidParams = InvibesBidParams.builder()
                .placementIds(Collections.singletonList(FIRST_PLACEMENT_ID))
                .bidVersion(BID_VERSION)
                .properties(properties)
                .build();

        final InvibesBidRequest expectedRequest = InvibesBidRequest.builder()
                .bidParamsJson(mapper.writeValueAsString(invibesBidParams))
                .isTestBid(true)
                .location(PAGE_URL)
                .isAmp(false)
                .gdpr(true)
                .gdprConsent(StringUtils.EMPTY)
                .invibBVLog(true)
                .videoAdDebug(true)
                .lid(BUYER_UID)
                .bvid("test")
                .width(String.valueOf(DEVICE_W))
                .height(String.valueOf(DEVICE_H))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(expectedRequest);
    }

    @Test
    public void makeHttpRequestsShouldCreateInvibesAmpBidRequestWithCorrectParams() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .device(Device.builder().w(DEVICE_W).h(DEVICE_H).build())
                        .user(User.builder().buyeruid(BUYER_UID).build())
                        .site(Site.builder().page(PAGE_URL).build())
                        .ext(ExtRequest.of(
                                ExtRequestPrebid.builder().amp(ExtRequestPrebidAmp.of(Collections.emptyMap())).build()
                        )),
                impBuilder -> impBuilder.banner(Banner.builder().h(BANNER_H).w(BANNER_W).build()));

        // when
        final Result<List<HttpRequest<InvibesBidRequest>>> result = invibesBidder.makeHttpRequests(bidRequest);

        // then
        final Map<String, InvibesPlacementProperty> properties = new HashMap<>();
        properties.put(FIRST_PLACEMENT_ID, InvibesPlacementProperty.builder()
                .formats(Collections.singletonList(Format.builder().w(BANNER_W).h(BANNER_H).build())).build());

        final InvibesBidParams invibesBidParams = InvibesBidParams.builder()
                .placementIds(Collections.singletonList(FIRST_PLACEMENT_ID))
                .bidVersion(BID_VERSION)
                .properties(properties)
                .build();

        final InvibesBidRequest expectedRequest = InvibesBidRequest.builder()
                .bidParamsJson(mapper.writeValueAsString(invibesBidParams))
                .isTestBid(true)
                .location(PAGE_URL)
                .isAmp(true)
                .gdpr(true)
                .gdprConsent(StringUtils.EMPTY)
                .invibBVLog(true)
                .videoAdDebug(true)
                .lid(BUYER_UID)
                .bvid("test")
                .width(String.valueOf(DEVICE_W))
                .height(String.valueOf(DEVICE_H))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(expectedRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<InvibesBidRequest> httpCall = givenHttpCall(null, "invalid");

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
        final BidderCall<InvibesBidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = invibesBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<InvibesBidRequest> httpCall = givenHttpCall(
                InvibesBidRequest.builder().build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid(IMP_ID))));

        // when
        final Result<List<BidderBid>> result = invibesBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid(IMP_ID).build(), banner, CURRENCY));
    }

    @Test
    public void makeBidsShouldReturnErrorIdBidResponseContainsError() throws JsonProcessingException {
        // given
        final BidderCall<InvibesBidRequest> httpCall = givenHttpCall(
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

    private static InvibesBidderResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return InvibesBidderResponse.builder()
                .typedBids(singletonList(InvibesTypedBid.builder()
                        .bid(bidCustomizer.apply(Bid.builder()).build())
                        .dealPriority(12)
                        .build()))
                .currency(CURRENCY)
                .build();
    }

    private static BidderCall<InvibesBidRequest> givenHttpCall(InvibesBidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<InvibesBidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            ExtImpInvibes extImpInvibes) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .site(Site.builder().page(PAGE_URL).build())
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
