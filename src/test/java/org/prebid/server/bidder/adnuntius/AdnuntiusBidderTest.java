package org.prebid.server.bidder.adnuntius;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.apache.commons.lang3.BooleanUtils;
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
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adnuntius.ExtImpAdnuntius;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

public class AdnuntiusBidderTest extends VertxTest {

    private AdnuntiusBidder target;

    @Before
    public void setUp() {
        final Clock clock = Clock.system(ZoneId.of("UTC+05:00"));
        target = new AdnuntiusBidder("https://test.domain.dm/uri", clock, jacksonMapper);
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
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsExactly("Fail on Imp.Id=test: Adnuntius supports only Banner");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSomeImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .allMatch(errorMessage -> errorMessage.startsWith("Unmarshalling error:"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithDimensionsIfBannerHighAndWidthArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(Banner.builder().w(150).h(200).build())));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusAdUnit::getDimensions)
                .containsExactly(List.of(List.of(150, 200)));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithDimensionsIfBannerFormatHighAndWidthArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(Banner.builder().format(
                        List.of(Format.builder().w(150).h(200).build())).build())));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusAdUnit::getDimensions)
                .containsExactly(List.of(List.of(150, 200)));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithEmptyDimensionsIfBannerFormatHighAndWidthAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.banner(Banner.builder().format(
                        List.of(Format.builder().build())).build())));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusAdUnit::getDimensions)
                .containsExactly(Collections.emptyList());
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAdUnitsSeparatedByImpExtNetwork() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(ExtImpAdnuntius.of("auId1", null, null), identity()),
                givenImp(ExtImpAdnuntius.of("auId2", null, null), identity()),
                givenImp(ExtImpAdnuntius.of("auId1", "network", null), identity()),
                givenImp(ExtImpAdnuntius.of("auId2", "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id(null)),
                givenImp(ExtImpAdnuntius.of("auId", null, null), imp -> imp.id(null)),
                givenImp(ExtImpAdnuntius.of("auId", null, null), imp -> imp.id("impId")));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

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
                givenImp(ExtImpAdnuntius.of(null, "network", null), identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

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
                givenImp(ExtImpAdnuntius.of(null, "network", null), identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getContext)
                .containsExactly("page", "page");
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfGdprAndConsentAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent(null).build()).build()),
                givenImp(identity()), givenImp(ExtImpAdnuntius.of(null, "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(null, null);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfGdprIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent("consent").build()).build()),
                givenImp(identity()), givenImp(ExtImpAdnuntius.of(null, "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(null, "consent");

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfConsentIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(1, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent(null).build()).build()),
                givenImp(identity()), givenImp(ExtImpAdnuntius.of(null, "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(1, null);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUri() {
        // given
        final Integer gdpr = 1;
        final String consent = "con sent";
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(gdpr, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent(consent).build()).build()),
                givenImp(identity()), givenImp(ExtImpAdnuntius.of(null, "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(gdpr, consent);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtImpNoCookiesIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                givenImp(identity()),
                givenImp(ExtImpAdnuntius.of(null, "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(null);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtImpNoCookiesIsFalse() {
        // given
        final Boolean noCookies = false;
        final BidRequest bidRequest = givenBidRequest(identity(),
                givenImp(identity()),
                givenImp(ExtImpAdnuntius.of(null, "network", noCookies), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(noCookies);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtImpNoCookiesIsTrue() {
        // given
        final Boolean noCookies = true;
        final BidRequest bidRequest = givenBidRequest(identity(),
                givenImp(identity()),
                givenImp(ExtImpAdnuntius.of(null, "network", noCookies), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(noCookies);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriAndPopulateExtDeviceWithNoCookies() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .device(Device.builder().ext(givenExtDeviceNoCookies(null)).build()),
                givenImp(identity()),
                givenImp(ExtImpAdnuntius.of(null, "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(null);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtDeviceImpNoCookiesIsFalse() {
        // given
        final Boolean noCookies = false;
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .device(Device.builder().ext(givenExtDeviceNoCookies(noCookies)).build()),
                givenImp(identity()),
                givenImp(ExtImpAdnuntius.of(null, "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(noCookies);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtDeviceImpNoCookiesIsTrue() {
        // given
        final Boolean noCookies = true;
        final BidRequest bidRequest = givenBidRequest(request -> request
                        .device(Device.builder().ext(givenExtDeviceNoCookies(noCookies)).build()),
                givenImp(identity()),
                givenImp(ExtImpAdnuntius.of(null, "network", null), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(noCookies);

        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall("Incorrect body");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

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
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall("null");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfResponseAdsUnitsIsNull() {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall("{}");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfResponseAdsUnitsIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipInvalidAdsUnits() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(givenAdsUnit());
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldUseCurrencyOfFirstBidOfLastAdsUnit() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnit(givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "1.1"))),
                        givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "1.2")))),
                givenAdsUnit(givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "2.1"))),
                        givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "2.2")))));

        final BidRequest bidRequest = givenBidRequest(givenImp(identity()), givenImp(identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).extracting(BidderBid::getBidCurrency)
                .containsExactly("2.1", "2.1");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfCreativeHeightOfSomeAdIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnit(givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "CUR")))),
                givenAdsUnit(givenAd(ad -> ad.creativeHeight(null))));

        final BidRequest bidRequest = givenBidRequest(givenImp(identity()), givenImp(identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsExactly("Value of measure: null can not be parsed.");
    }

    @Test
    public void makeBidsShouldReturnErrorIfCreativeWidthtOfSomeAdIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnit(givenAd(ad -> ad.bid(AdnuntiusBid.of(null, "CUR")))),
                givenAdsUnit(givenAd(ad -> ad.creativeWidth(null))));

        final BidRequest bidRequest = givenBidRequest(givenImp(identity()), givenImp(identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsExactly("Value of measure: null can not be parsed.");
    }

    @Test
    public void makeBidsShouldReturnCorrectSeatBids() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(givenAdsUnit(givenAd(ad -> ad
                .bid(AdnuntiusBid.of(BigDecimal.ONE, "CUR"))
                .adId("adId")
                .creativeId("creativeId")
                .lineItemId("lineItemId")
                .dealId("dealId")
                .destinationUrls(Map.of("key1", "https://www.domain1.com/uri",
                        "key2", "http://www.domain2.dt/uri")))));

        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("impId")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

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
                assertThat(bid).extracting(Bid::getDealid).isEqualTo("dealId");
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

    @Test
    public void makeBidsShouldReturnErrorWhenAdsUnitsCountGreaterThanImpsCount() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnit(givenAd(identity())),
                givenAdsUnit(givenAd(identity())));

        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsExactly("Impressions count is less then ads units count.");
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
        return impCustomizer.apply(Imp.builder().id("test").banner(banner).ext(ext)).build();
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(ExtImpAdnuntius.of(null, null, null), impCustomizer);
    }

    private BidderCall<AdnuntiusRequest> givenHttpCall(String body) {
        final HttpRequest<AdnuntiusRequest> request = HttpRequest.<AdnuntiusRequest>builder().build();
        final HttpResponse response = HttpResponse.of(200, null, body);
        return BidderCall.succeededHttp(request, response, null);
    }

    private BidderCall<AdnuntiusRequest> givenHttpCall(AdnuntiusAdsUnit... adsUnits)
            throws JsonProcessingException {
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

    private static ExtDevice givenExtDeviceNoCookies(Boolean noCookies) {
        final ExtDevice extDevice = ExtDevice.empty();
        extDevice.addProperty("noCookies", noCookies != null
                ? BooleanNode.valueOf(noCookies)
                : NullNode.getInstance());
        return extDevice;
    }

    private static String givenExpectedUrl(Integer gdpr, String consent) {
        return buildExpectedUrl(gdpr, consent, false);
    }

    private static String givenExpectedUrl(Boolean noCookies) {
        return buildExpectedUrl(null, null, noCookies);
    }

    private static String buildExpectedUrl(Integer gdpr, String consent, Boolean noCookies) {
        final StringBuilder expectedUri = new StringBuilder("https://test.domain.dm/uri?format=json&tzo=-300");
        if (gdpr != null && consent != null) {
            expectedUri.append("&gdpr=").append(HttpUtil.encodeUrl(gdpr.toString()));
            expectedUri.append("&consentString=").append(HttpUtil.encodeUrl(consent));
        }
        if (BooleanUtils.isTrue(noCookies)) {
            expectedUri.append("&noCookies=").append(HttpUtil.encodeUrl(noCookies.toString()));
        }
        return expectedUri.toString();
    }
}
