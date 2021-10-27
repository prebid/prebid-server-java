package org.prebid.server.bidder.criteo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.criteo.model.CriteoGdprConsent;
import org.prebid.server.bidder.criteo.model.CriteoPublisher;
import org.prebid.server.bidder.criteo.model.CriteoRequest;
import org.prebid.server.bidder.criteo.model.CriteoRequestSlot;
import org.prebid.server.bidder.criteo.model.CriteoResponse;
import org.prebid.server.bidder.criteo.model.CriteoResponseSlot;
import org.prebid.server.bidder.criteo.model.CriteoUser;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpCriteo;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class CriteoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";
    private static final String UUID_REGEX = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
            + "-[0-9a-fA-F]{12}";

    private CriteoBidder criteoBidder;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        criteoBidder = new CriteoBidder(ENDPOINT_URL, jacksonMapper, false);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() ->
                new CriteoBidder("invalid_url", jacksonMapper, false));
    }

    @Test
    public void makeHttpRequestShouldThrowErrorIfImpsNetworkIdIsDifferent() {
        // given
        final BidRequest bidRequest =
                BidRequest.builder()
                        .imp(Arrays.asList(
                                Imp.builder()
                                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpCriteo.of(1, 1))))
                                        .build(),
                                Imp.builder()
                                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpCriteo.of(1, 2))))
                                        .build()))
                        .build();

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Bid request has slots coming with several "
                        + "network IDs which is not allowed"));
        assertThat(result.getValue()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value of");
                });
    }

    @Test
    public void makeHttpRequestsShouldResolveSlotSizesFromBannerFormat() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().format(singletonList(Format.builder()
                        .w(222)
                        .h(333)
                        .build()))
                        .build()));

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CriteoRequest::getSlots)
                .flatExtracting(CriteoRequestSlot::getSizes)
                .containsExactly("222x333");
    }

    @Test
    public void makeHttpRequestsShouldGenerateSlotIdIfGenerateIdPropertyIsTrue() {
        // given
        criteoBidder = new CriteoBidder(ENDPOINT_URL, jacksonMapper, true);
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CriteoRequest::getSlots)
                .extracting(CriteoRequestSlot::getSlotId)
                .allSatisfy(slotId -> assertThat(slotId).matches(UUID_REGEX));
    }

    @Test
    public void makeHttpRequestsShouldBuildRequestWithCriteoPublisher() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(CriteoRequest::getPublisher)
                .containsExactly(CriteoPublisher.builder()
                        .siteId("siteId")
                        .url("www.criteo.com")
                        .networkId(1)
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCreateSpecificForAndroidDeviceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .device(Device.builder().os("android").build()).build();

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(CriteoRequest::getUser)
                .extracting(CriteoUser::getDeviceOs, CriteoUser::getDeviceIdType)
                .containsExactly(tuple("android", "gaid"));
    }

    @Test
    public void makeHttpRequestsShouldCreateSpecificForUnknownDeviceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .device(Device.builder().os("somethingNew").build()).build();

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(CriteoRequest::getUser)
                .extracting(CriteoUser::getDeviceOs, CriteoUser::getDeviceIdType)
                .containsExactly(tuple("somethingNew", "unknown"));
    }

    @Test
    public void makeHttpRequestsShouldBuildRequestWithCriteoUser() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(CriteoRequest::getUser)
                .containsExactly(CriteoUser.builder()
                        .cookieId("buyerid")
                        .deviceId("ifa")
                        .deviceIdType("idfa")
                        .deviceOs("ios")
                        .ip("255.255.255.255")
                        .userAgent("userAgent")
                        .uspIab("1N--")
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldBuildRequestWithCriteoGdprConsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(CriteoRequest::getGdprConsent)
                .containsExactly(CriteoGdprConsent.builder()
                        .consentData("consent")
                        .gdprApplies(true)
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldSetCookieUidHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpUtil.COOKIE_HEADER.toString(), "uid=buyerid"));
    }

    @Test
    public void makeHttpRequestsShouldSetUserAgentAndForwarderForHeadersIfBidRequestDeviceIsNotNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CriteoRequest>>> result = criteoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "userAgent"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "255.255.255.255"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<CriteoRequest> httpCall = HttpCall.success(
                HttpRequest.<CriteoRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, "invalid"),
                null);

        // when
        final Result<List<BidderBid>> result = criteoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBids() throws JsonProcessingException {
        // given
        final HttpCall<CriteoRequest> httpCall = givenHttpCall(identity());

        // when
        final Result<List<BidderBid>> result = criteoBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .contains(BidderBid.of(
                        Bid.builder()
                                .id("slot_id")
                                .impid("imp_id")
                                .price(BigDecimal.valueOf(0.05))
                                .adm("creative")
                                .w(300)
                                .h(300)
                                .crid("creative-id").build(), BidType.banner, "USD"));
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .id("bid-request-id")
                .imp(singletonList(givenImp(impCustomizer))))
                .user(User.builder().buyeruid("buyerid").ext(ExtUser.builder().consent("consent").build()).build())
                .device(Device.builder().os("ios").ifa("ifa").ip("255.255.255.255").ua("userAgent").build())
                .site(Site.builder().id("siteId").page("www.criteo.com").build())
                .regs(Regs.of(null, ExtRegs.of(1, "1N--")))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("imp_id")
                .banner(Banner.builder()
                        .id("banner_id")
                        .h(300)
                        .w(300)
                        .build()
                )
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpCriteo.of(1, 1)))))
                .build();
    }

    private static HttpCall<CriteoRequest> givenHttpCall(
            Function<CriteoResponse.CriteoResponseBuilder,
                    CriteoResponse.CriteoResponseBuilder> responseCustomizer) throws JsonProcessingException {
        final CriteoResponse.CriteoResponseBuilder responseBuilder =
                CriteoResponse.builder()
                        .id("response-id")
                        .slots(singletonList(
                                CriteoResponseSlot.builder()
                                        .arbitrageId("slot_id")
                                        .impId("imp_id")
                                        .width(300)
                                        .height(300)
                                        .networkId(1)
                                        .zoneId(1)
                                        .cpm(BigDecimal.valueOf(0.05))
                                        .creative("creative")
                                        .creativeCode("creative-id")
                                        .currency("USD")
                                        .build()));

        final String body = mapper.writeValueAsString(
                responseCustomizer.apply(responseBuilder).build());

        return HttpCall.success(
                HttpRequest.<CriteoRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
