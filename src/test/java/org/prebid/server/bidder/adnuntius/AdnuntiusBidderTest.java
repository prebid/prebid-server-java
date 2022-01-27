package org.prebid.server.bidder.adnuntius;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusAdUnit;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusMetaData;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusRequest;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAd;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdsUnit;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusBid;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adnuntius.ExtImpAdnuntius;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

public class AdnuntiusBidderTest extends VertxTest {

    private AdnuntiusBidder bidder;

    @Before
    public void setUp() {
        final Clock clock = Clock.system(ZoneId.of("UTC+05:00"));
        bidder = new AdnuntiusBidder("https://test.domain.dm/uri", clock, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new AdnuntiusBidder("invalid_url", Clock.systemDefaultZone(), jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSomeImpBannerIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()), givenImp(imp -> imp.banner(null)));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsExactly("Fail on Imp.Id=null: Adnuntius supports only Banner");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSomeImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .allMatch(errorMessage -> errorMessage.startsWith("Unmarshalling error:"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAdUnitsSeparatedByImpExtNetwork() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(ExtImpAdnuntius.of("auId1", null), identity()),
                givenImp(ExtImpAdnuntius.of("auId2", null), identity()),
                givenImp(ExtImpAdnuntius.of("auId1", "network"), identity()),
                givenImp(ExtImpAdnuntius.of("auId2", "network"), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getAdUnits)
                .allSatisfy(adUnits -> assertThat(adUnits).extracting(AdnuntiusAdUnit::getAuId)
                        .containsExactly("auId1", "auId2"));
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectAdUnits() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(ExtImpAdnuntius.of("auId", null), identity()),
                givenImp(ExtImpAdnuntius.of("auId", null), imp -> imp.id("impId")));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusAdUnit::getAuId, AdnuntiusAdUnit::getTargetId)
                .containsExactly(tuple(null, "null-null"), tuple("auId", "auId-null"), tuple("auId", "auId-impId"));
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithMetaDataIfUserIdIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.user(User.builder().id("userId").build()),
                givenImp(ExtImpAdnuntius.of(null, "network"), identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getMetaData)
                .extracting(AdnuntiusMetaData::getUsi)
                .containsExactly("userId", "userId");
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithHeadersIfDeviceIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .device(Device.builder().ip("ip").ua("ua").build()),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("User-Agent", "ua"),
                        tuple("X-Forwarded-For", "ip"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithContextIfSitePageIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.site(Site.builder().page("page").build()),
                givenImp(ExtImpAdnuntius.of(null, "network"), identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getContext)
                .containsExactly("page", "page");
        assertThat(result.getErrors()).hasSize(0);
    }

    private void makeHttpRequestsShouldReturnRequestsWithCorrectUri(Integer gdpr, String consent) {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .regs(Regs.of(null, ExtRegs.of(gdpr, null)))
                        .user(User.builder().ext(ExtUser.builder().consent(consent).build()).build()),
                givenImp(identity()), givenImp(ExtImpAdnuntius.of(null, "network"), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        final StringBuilder expectedUri = new StringBuilder("https://test.domain.dm/uri?format=json&tzo=-300");
        if (gdpr != null && consent != null) {
            expectedUri.append("&gdpr=").append(HttpUtil.encodeUrl(gdpr.toString()));
            expectedUri.append("&consentString=").append(HttpUtil.encodeUrl(consent));
        }

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUri.toString(), expectedUri.toString());
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfGdprAndConsentAreAbsent() {
        makeHttpRequestsShouldReturnRequestsWithCorrectUri(null, null);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfGdprIsAbsent() {
        makeHttpRequestsShouldReturnRequestsWithCorrectUri(null, "con sent");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfConsentIsAbsent() {
        makeHttpRequestsShouldReturnRequestsWithCorrectUri(1, null);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfGdprAndConsentArePresent() {
        makeHttpRequestsShouldReturnRequestsWithCorrectUri(1, "con sent");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall("Incorrect body");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfResponseIsNull() {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall("null");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfResponseAdsUnitsIsNull() {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall("{}");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfResponseAdsUnitsIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall();

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipInvalidAdsUnits() throws JsonProcessingException {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall(givenAdsUnit());

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldUseCurrencyOfFirstBidOfLastAdsUnit() throws JsonProcessingException {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnit(givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "1.1"))),
                        givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "1.2")))),
                givenAdsUnit(givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "2.1"))),
                        givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "2.2")))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).extracting(BidderBid::getBidCurrency)
                .containsExactly("2.1", "2.1");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfCreativeHeightOfSomeAdIsAbsent() throws JsonProcessingException {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnit(givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "CUR")))),
                givenAdsUnit(givenAd(ad -> ad.creativeHeight(null))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsExactly("Value of measure: null can not be parsed.");
    }

    @Test
    public void makeBidsShouldReturnErrorIfCreativeWidthtOfSomeAdIsAbsent() throws JsonProcessingException {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnit(givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "CUR")))),
                givenAdsUnit(givenAd(ad -> ad.creativeWidth(null))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsExactly("Value of measure: null can not be parsed.");
    }

    @Test
    public void makeBidsShouldReturnCorrectSeatBids() throws JsonProcessingException {
        // given
        final HttpCall<AdnuntiusRequest> httpCall = givenHttpCall(givenAdsUnit(givenAd(ad -> ad
                .bid(AdnuntiusBid.of(BigDecimal.ONE, "CUR"))
                .adId("adId")
                .creativeId("creativeId")
                .lineItemId("lineItemId")
                .destinationUrls(Map.of("key1", "https://www.domain1.com/uri",
                        "key2", "http://www.domain2.dt/uri")))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(1).allSatisfy(bidderBid -> {
            assertThat(bidderBid).extracting(BidderBid::getBid).satisfies(bid -> {
                assertThat(bid).extracting(Bid::getId).isEqualTo("adId");
                assertThat(bid).extracting(Bid::getImpid).isEqualTo("impId");
                assertThat(bid).extracting(Bid::getW).isEqualTo(21);
                assertThat(bid).extracting(Bid::getH).isEqualTo(9);
                assertThat(bid).extracting(Bid::getAdid).isEqualTo("adId");
                assertThat(bid).extracting(Bid::getAdm).isEqualTo("html");
                assertThat(bid).extracting(Bid::getCid).isEqualTo("lineItemId");
                assertThat(bid).extracting(Bid::getCrid).isEqualTo("creativeId");
                assertThat(bid).extracting(Bid::getPrice).isEqualTo(BigDecimal.valueOf(1000));
                assertThat(bid).extracting(Bid::getAdomain).asList()
                        .containsExactlyInAnyOrder("domain1.com", "domain2.dt");
            });
            assertThat(bidderBid).extracting(BidderBid::getType).isEqualTo(BidType.banner);
            assertThat(bidderBid).extracting(BidderBid::getBidCurrency).isEqualTo("CUR");
        });
        assertThat(result.getErrors()).isEmpty();
    }

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer, Imp... imps) {
        return bidRequestCustomizer.apply(BidRequest.builder()).imp(List.of(imps)).build();
    }

    private BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private Imp givenImp(ExtImpAdnuntius extImpAdnuntius, UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final Banner banner = Banner.builder().build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpAdnuntius));
        return impCustomizer.apply(Imp.builder().banner(banner).ext(ext)).build();
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(ExtImpAdnuntius.of(null, null), impCustomizer);
    }

    private HttpCall<AdnuntiusRequest> givenHttpCall(String body) {
        final HttpRequest<AdnuntiusRequest> request = HttpRequest.<AdnuntiusRequest>builder().build();
        final HttpResponse response = HttpResponse.of(200, null, body);
        return HttpCall.success(request, response, null);
    }

    private HttpCall<AdnuntiusRequest> givenHttpCall(AdnuntiusAdsUnit... adsUnits) throws JsonProcessingException {
        return givenHttpCall(mapper.writeValueAsString(AdnuntiusResponse.of(List.of(adsUnits))));
    }

    private AdnuntiusAdsUnit givenAdsUnit(AdnuntiusAd... ads) {
        return AdnuntiusAdsUnit.builder()
                .auId("auId")
                .targetId("auId-impId")
                .html("html")
                .ads(List.of(ads))
                .build();
    }

    private AdnuntiusAd givenAd(UnaryOperator<AdnuntiusAd.AdnuntiusAdBuilder> customizer) {
        return customizer.apply(AdnuntiusAd.builder().creativeWidth("21").creativeHeight("9")).build();
    }
}
