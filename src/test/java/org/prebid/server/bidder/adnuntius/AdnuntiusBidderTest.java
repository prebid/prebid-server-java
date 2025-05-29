package org.prebid.server.bidder.adnuntius;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusNativeRequest;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusRequestAdUnit;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusMetaData;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusRequest;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAd;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdUnit;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdvertiser;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusBid;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusGrossBid;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusNetBid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adnuntius.ExtImpAdnuntius;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

public class AdnuntiusBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.domain.dm/uri";
    private static final String EU_ENDPOINT_URL = "https://alternative.domain.dm/uri";

    private AdnuntiusBidder target;

    @BeforeEach
    public void setUp() {
        final Clock clock = Clock.system(ZoneId.of("UTC+05:00"));
        target = new AdnuntiusBidder(
                ENDPOINT_URL,
                EU_ENDPOINT_URL,
                clock,
                jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdnuntiusBidder(
                "invalid_url",
                null,
                Clock.systemDefaultZone(),
                jacksonMapper));
    }

    @Test
    public void creationShouldFailOnInvalidEuEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdnuntiusBidder(
                ENDPOINT_URL,
                "invalid_url",
                Clock.systemDefaultZone(),
                jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSomeImpBannerAndNativeIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(identity()),
                givenNativeImp(identity()),
                givenBannerImp(imp -> imp.banner(null).xNative(null)));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsExactly("ignoring imp id=impId: Adnuntius supports only native and banner");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSomeImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(identity()),
                givenBannerImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .allMatch(errorMessage -> errorMessage.startsWith("Unmarshalling error:"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithMaxDealsIfMaxDealsIsBiggestThatZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().maxDeals(10).build(), identity()),
                givenNativeImp(ExtImpAdnuntius.builder().maxDeals(5).build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getMaxDeals)
                .containsExactly(10, 5);
    }

    @Test
    public void makeHttpRequestsShouldNotReturnRequestsWithMaxDealsIfMaxDealsIsLowestThatZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().maxDeals(-10).build(), identity()),
                givenNativeImp(ExtImpAdnuntius.builder().maxDeals(-10).build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getMaxDeals)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldReturnAdTypeForNativeImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenNativeImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getAdType)
                .containsOnly("NATIVE");
    }

    @Test
    public void makeHttpRequestsShouldNotReturnAdTypeForBannerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getAdType)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithDimensionsIfBannerHighAndWidthArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(imp -> imp.banner(Banner.builder().w(150).h(200).build())));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getDimensions)
                .containsExactly(List.of(List.of(150, 200)));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithDimensionsIfBannerFormatHighAndWidthArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(imp -> imp.banner(Banner.builder()
                        .format(List.of(
                                Format.builder().w(150).h(200).build(),
                                Format.builder().w(100).h(300).build()))
                        .w(50)
                        .h(350)
                        .build())));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getDimensions)
                .containsExactly(List.of(List.of(150, 200), List.of(100, 300)));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithoutDimensionsIfBannerFormatHighAndWidthAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getDimensions)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldReturnNativeRequestAdUnitForNativeImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenNativeImp(imp ->
                imp.xNative(Native.builder().request("{\"field\":\"value\"}").build())));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getNativeRequest)
                .extracting(AdnuntiusNativeRequest::getOrtb)
                .allSatisfy(ortb -> assertThat(ortb).isEqualTo(mapper.createObjectNode().put("field", "value")));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAdUnitsSeparatedByBannerImpExtNetwork() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId1").build(), identity()),
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId2").build(), identity()),
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId1").network("network").build(), identity()),
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId2").network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getAdUnits)
                .allSatisfy(adUnits -> assertThat(adUnits)
                        .extracting(AdnuntiusRequestAdUnit::getAuId)
                        .containsExactly("auId1", "auId2"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAdUnitsSeparatedByNativeImpExtNetwork() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenNativeImp(ExtImpAdnuntius.builder().auId("auId1").build(), identity()),
                givenNativeImp(ExtImpAdnuntius.builder().auId("auId2").build(), identity()),
                givenNativeImp(ExtImpAdnuntius.builder().auId("auId1").network("network").build(), identity()),
                givenNativeImp(ExtImpAdnuntius.builder().auId("auId2").network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getAdUnits)
                .allSatisfy(adUnits -> assertThat(adUnits)
                        .extracting(AdnuntiusRequestAdUnit::getAuId)
                        .containsExactly("auId1", "auId2"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectAdUnits() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerAndNativeImp(ExtImpAdnuntius.builder().build(), imp -> imp.id(null)),
                givenBannerAndNativeImp(ExtImpAdnuntius.builder().auId("auId").build(), imp -> imp.id(null)),
                givenBannerAndNativeImp(ExtImpAdnuntius.builder().auId("auId").build(), imp -> imp.id("impId")));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(AdnuntiusRequest::getAdUnits)
                .extracting(AdnuntiusRequestAdUnit::getAuId, AdnuntiusRequestAdUnit::getTargetId)
                .containsExactly(
                        tuple(null, "null-null:banner"),
                        tuple(null, "null-null:native"),
                        tuple("auId", "auId-null:banner"),
                        tuple("auId", "auId-null:native"),
                        tuple("auId", "auId-impId:banner"),
                        tuple("auId", "auId-impId:native"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithTheWholeListOfImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerAndNativeImp(
                        ExtImpAdnuntius.builder().auId("auId").build(), imp -> imp.id("impId1")),
                givenNativeImp(
                        ExtImpAdnuntius.builder().auId("auId").build(), imp -> imp.id("impId2")),
                givenBannerImp(
                        ExtImpAdnuntius.builder().auId("auId").network("network").build(), imp -> imp.id("impId3")));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder(Set.of("impId1", "impId2", "impId3"), Set.of("impId1", "impId2", "impId3"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithMetaDataIfUserIdIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder().id("userId").build()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getMetaData)
                .extracting(AdnuntiusMetaData::getUsi)
                .containsExactly("userId", "userId");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldPopulateMetaDataUsiFromUserIdWhenBothUidIdAndUserIdPresentInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder()
                        .id("userId")
                        .ext(ExtUser.builder()
                                .eids(List.of(Eid.builder().uids(List.of(Uid.builder().id("eidsId").build())).build()))
                                .build())
                        .build()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getMetaData)
                .extracting(AdnuntiusMetaData::getUsi)
                .containsExactly("userId", "userId");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldPopulateMetaDataUsiWhenUserExtEidsUidIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder()
                        .id(null)
                        .ext(ExtUser.builder()
                                .eids(List.of(Eid.builder().uids(List.of(Uid.builder().id("eidsId").build())).build()))
                                .build())
                        .build()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getMetaData)
                .extracting(AdnuntiusMetaData::getUsi)
                .containsExactly("eidsId", "eidsId");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldPopulateHttpRequestKeyValueFieldFromSiteExtDataWhenDataIsPresentInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder()
                        .ext(ExtSite.of(null, mapper.createObjectNode().put("ANY", "ANY")))
                        .build()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getKeyValue)
                .containsExactly(
                        mapper.createObjectNode().put("ANY", "ANY"),
                        mapper.createObjectNode().put("ANY", "ANY"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithHeadersIfDeviceIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder().ip("ip").ua("ua").build()),
                givenBannerImp(identity()));

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
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().page("page").build()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getPayload)
                .extracting(AdnuntiusRequest::getContext)
                .containsExactly("page", "page");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfGdprAndConsentAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent(null).build()).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = buildExpectedUrl(ENDPOINT_URL, null, null, null);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfGdprIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent("consent").build()).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, null, "consent");

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfConsentIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder().ext(ExtUser.builder().consent(null).build()).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, null);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUri() {
        // given
        final Integer gdpr = 1;
        final String consent = "con sent";
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(gdpr, null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent(consent).build()).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(EU_ENDPOINT_URL, gdpr, consent);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtImpNoCookiesIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, null);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtImpNoCookiesIsFalse() {
        // given
        final Boolean noCookies = false;
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").noCookies(noCookies).build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, noCookies);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtImpNoCookiesIsTrue() {
        // given
        final Boolean noCookies = true;
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").noCookies(noCookies).build(), identity()),
                givenBannerImp(ExtImpAdnuntius.builder().noCookies(!noCookies).build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, noCookies);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriAndPopulateExtDeviceWithNoCookies() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder().ext(givenExtDeviceNoCookies(null)).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, null);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtDeviceImpNoCookiesIsFalse() {
        // given
        final Boolean noCookies = false;
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder().ext(givenExtDeviceNoCookies(noCookies)).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, noCookies);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithCorrectUriIfExtDeviceImpNoCookiesIsTrue() {
        // given
        final Boolean noCookies = true;
        final BidRequest bidRequest = givenBidRequest(
                request -> request.device(Device.builder().ext(givenExtDeviceNoCookies(noCookies)).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, noCookies);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithBasicUriIfGdprAndConsentAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent(null).build()).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = buildExpectedUrl(ENDPOINT_URL, null, null, null);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithBasicUriIfGdprIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(null, null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent("consent").build()).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(ENDPOINT_URL, null, "consent");

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAlternativeUriIfConsentIsAbsentAndGdprIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent(null).build()).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = buildExpectedUrl(EU_ENDPOINT_URL, 1, null, null);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAlternativeUri() {
        // given
        final Integer gdpr = 1;
        final String consent = "con sent";
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(gdpr, null, null, null)).build())
                        .user(User.builder().ext(ExtUser.builder().consent(consent).build()).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(EU_ENDPOINT_URL, gdpr, consent);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAlternativeUriIfExtImpNoCookiesIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = buildExpectedUrl(EU_ENDPOINT_URL, 1, null, null);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAlternativeUriIfExtImpNoCookiesIsFalse() {
        // given
        final Boolean noCookies = false;
        final BidRequest bidRequest = givenBidRequest(
                request -> request.regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").noCookies(noCookies).build(), identity()),
                givenBannerImp(identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(EU_ENDPOINT_URL, 1, noCookies);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAlternativeUriIfExtImpNoCookiesIsTrue() {
        // given
        final Boolean noCookies = true;
        final BidRequest bidRequest = givenBidRequest(
                request -> request.regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").noCookies(noCookies).build(), identity()),
                givenBannerImp(ExtImpAdnuntius.builder().noCookies(!noCookies).build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(EU_ENDPOINT_URL, 1, noCookies);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAlternativeUriAndPopulateExtDeviceWithNoCookies() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build())
                        .device(Device.builder().ext(givenExtDeviceNoCookies(null)).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = buildExpectedUrl(EU_ENDPOINT_URL, 1, null, null);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAlternativeUriIfExtDeviceImpNoCookiesIsFalse() {
        // given
        final Boolean noCookies = false;
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build())
                        .device(Device.builder().ext(givenExtDeviceNoCookies(noCookies)).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(EU_ENDPOINT_URL, 1, noCookies);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAlternativeUriIfExtDeviceImpNoCookiesIsTrue() {
        // given
        final Boolean noCookies = true;
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build())
                        .device(Device.builder().ext(givenExtDeviceNoCookies(noCookies)).build()),
                givenBannerImp(identity()),
                givenBannerImp(ExtImpAdnuntius.builder().network("network").build(), identity()));

        // when
        final Result<List<HttpRequest<AdnuntiusRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = givenExpectedUrl(EU_ENDPOINT_URL, 1, noCookies);

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(expectedUrl, expectedUrl);
        assertThat(result.getErrors()).isEmpty();
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
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(givenAdsUnitWithAds("auId"));
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldUseCurrencyOfFirstBidOfLastRelatedImp() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnitWithAds(
                        "au1",
                        givenAd(ad -> ad.bid(AdnuntiusBid.of(BigDecimal.ONE, "1.1"))),
                        givenAd(ad -> ad.bid(AdnuntiusBid.of(BigDecimal.ONE, "1.2")))),
                givenAdsUnitWithAds(
                        "au2",
                        givenAd(ad -> ad.bid(AdnuntiusBid.of(BigDecimal.ONE, "2.1"))),
                        givenAd(ad -> ad.bid(AdnuntiusBid.of(BigDecimal.ONE, "2.2")))));

        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().auId("au2").build(), identity()),
                givenBannerImp(ExtImpAdnuntius.builder().auId("au1").build(), identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("1.1", "1.1");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldPopulateGrossBidPriceWhenGrossBidSpecified() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnitWithAds("auId", givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.TWO, "USD"))
                        .grossBid(AdnuntiusGrossBid.of(BigDecimal.ONE)))));
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId").bidType("gross").build(), identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(1000));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldPopulateNetBidPriceWhenGrossBidSpecified() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnitWithAds("auId", givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.TWO, "USD"))
                        .netBid(AdnuntiusNetBid.of(BigDecimal.ONE)))));
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId").bidType("net").build(), identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(1000));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldNotReturnBidFromDealsWhenAdsIsAbsentAndDealsIsSpecified() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(givenAdsUnitWithDeals("auId", givenAd(identity())));

        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId").build(), identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnTwoBidFromDealsAndAdsWhenAdsAndDealsIsSpecified() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(givenBannerAdsUnitWithDealsAndAds(
                "auId",
                List.of(givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.ONE, "USD"))
                        .adId("adId")
                        .creativeId("creativeId")
                        .lineItemId("lineItemId")
                        .dealId("dealId")
                        .advertiser(AdnuntiusAdvertiser.of(null, "name"))
                        .advertiserDomains(List.of("domain1.com", "domain2.dt")))),
                List.of(givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.ONE, "USD"))
                        .adId("adId")
                        .creativeId("creativeId")
                        .lineItemId("lineItemId")
                        .dealId("dealId")
                        .html("dealHtml")
                        .advertiser(AdnuntiusAdvertiser.of("legalName", "name"))
                        .advertiserDomains(List.of("domain1.com", "domain2.dt"))))));

        final BidRequest bidRequest = givenBidRequest(givenBannerImp(
                ExtImpAdnuntius.builder().auId("auId").build(), identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2).zipSatisfy(asList("html", "dealHtml"), (bidderBid, html) -> {
            assertThat(bidderBid).extracting(BidderBid::getBid).satisfies(bid -> {
                assertThat(bid).extracting(Bid::getId).isEqualTo("adId");
                assertThat(bid).extracting(Bid::getImpid).isEqualTo("impId");
                assertThat(bid).extracting(Bid::getW).isEqualTo(21);
                assertThat(bid).extracting(Bid::getH).isEqualTo(9);
                assertThat(bid).extracting(Bid::getAdid).isEqualTo("adId");
                assertThat(bid).extracting(Bid::getAdm).isEqualTo(html);
                assertThat(bid).extracting(Bid::getCid).isEqualTo("lineItemId");
                assertThat(bid).extracting(Bid::getDealid).isEqualTo("dealId");
                assertThat(bid).extracting(Bid::getCrid).isEqualTo("creativeId");
                assertThat(bid).extracting(Bid::getMtype).isEqualTo(1);
                assertThat(bid).extracting(Bid::getPrice).isEqualTo(BigDecimal.valueOf(1000));
                assertThat(bid).extracting(Bid::getAdomain).asList()
                        .containsExactlyInAnyOrder("domain1.com", "domain2.dt");
                assertThat(bid).extracting(Bid::getExt).isNull();
            });
            assertThat(bidderBid).extracting(BidderBid::getType).isEqualTo(BidType.banner);
            assertThat(bidderBid).extracting(BidderBid::getBidCurrency).isEqualTo("USD");
        });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnTwoBidFromDealsAndAdsWhenNativeAdsAndDealsIsSpecified()
            throws JsonProcessingException {

        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(givenNativeAdsUnitWithDealsAndAds(
                "auId",
                List.of(givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.ONE, "USD"))
                        .adId("adId")
                        .creativeId("creativeId")
                        .lineItemId("lineItemId")
                        .dealId("dealId")
                        .advertiser(AdnuntiusAdvertiser.of(null, "name"))
                        .advertiserDomains(List.of("domain1.com", "domain2.dt")))),
                List.of(givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.ONE, "USD"))
                        .adId("adId")
                        .creativeId("creativeId")
                        .lineItemId("lineItemId")
                        .dealId("dealId")
                        .html("dealHtml")
                        .advertiser(AdnuntiusAdvertiser.of("legalName", "name"))
                        .advertiserDomains(List.of("domain1.com", "domain2.dt")))),
                "{\"ortb\":{\"property\":\"value\"}}"));

        final BidRequest bidRequest = givenBidRequest(givenBannerImp(
                ExtImpAdnuntius.builder().auId("auId").build(), identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2);

        assertThat(result.getValue().getFirst()).satisfies(bidderBid -> {
            assertThat(bidderBid).extracting(BidderBid::getBid).satisfies(bid -> {
                assertThat(bid).extracting(Bid::getId).isEqualTo("adId");
                assertThat(bid).extracting(Bid::getImpid).isEqualTo("impId");
                assertThat(bid).extracting(Bid::getW).isEqualTo(21);
                assertThat(bid).extracting(Bid::getH).isEqualTo(9);
                assertThat(bid).extracting(Bid::getAdid).isEqualTo("adId");
                assertThat(bid).extracting(Bid::getAdm).isEqualTo("{\"property\":\"value\"}");
                assertThat(bid).extracting(Bid::getCid).isEqualTo("lineItemId");
                assertThat(bid).extracting(Bid::getDealid).isEqualTo("dealId");
                assertThat(bid).extracting(Bid::getCrid).isEqualTo("creativeId");
                assertThat(bid).extracting(Bid::getPrice).isEqualTo(BigDecimal.valueOf(1000));
                assertThat(bid).extracting(Bid::getMtype).isEqualTo(4);
                assertThat(bid).extracting(Bid::getAdomain).asList()
                        .containsExactlyInAnyOrder("domain1.com", "domain2.dt");
                assertThat(bid).extracting(Bid::getExt).isNull();
            });
            assertThat(bidderBid).extracting(BidderBid::getType).isEqualTo(BidType.xNative);
            assertThat(bidderBid).extracting(BidderBid::getBidCurrency).isEqualTo("USD");
        });

        assertThat(result.getValue().getLast()).satisfies(bidderBid -> {
            assertThat(bidderBid).extracting(BidderBid::getBid).satisfies(bid -> {
                assertThat(bid).extracting(Bid::getId).isEqualTo("adId");
                assertThat(bid).extracting(Bid::getImpid).isEqualTo("impId");
                assertThat(bid).extracting(Bid::getW).isEqualTo(21);
                assertThat(bid).extracting(Bid::getH).isEqualTo(9);
                assertThat(bid).extracting(Bid::getAdid).isEqualTo("adId");
                assertThat(bid).extracting(Bid::getAdm).isEqualTo("dealHtml");
                assertThat(bid).extracting(Bid::getCid).isEqualTo("lineItemId");
                assertThat(bid).extracting(Bid::getDealid).isEqualTo("dealId");
                assertThat(bid).extracting(Bid::getCrid).isEqualTo("creativeId");
                assertThat(bid).extracting(Bid::getPrice).isEqualTo(BigDecimal.valueOf(1000));
                assertThat(bid).extracting(Bid::getMtype).isEqualTo(1);
                assertThat(bid).extracting(Bid::getAdomain).asList()
                        .containsExactlyInAnyOrder("domain1.com", "domain2.dt");
                assertThat(bid).extracting(Bid::getExt).isNull();
            });
            assertThat(bidderBid).extracting(BidderBid::getType).isEqualTo(BidType.banner);
            assertThat(bidderBid).extracting(BidderBid::getBidCurrency).isEqualTo("USD");
        });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnTwoBidWithDsaFromDealsAndAdsWhenAdsAndDealsIsSpecifiedAndDsaReturned()
            throws JsonProcessingException {

        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(givenBannerAdsUnitWithDealsAndAds(
                "auId",
                List.of(givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.ONE, "USD"))
                        .adId("adId")
                        .creativeId("creativeId")
                        .lineItemId("lineItemId")
                        .dealId("dealId")
                        .advertiser(AdnuntiusAdvertiser.of(null, "name"))
                        .destinationUrls(Map.of(
                                "key1", "https://www.domain1.com/uri",
                                "key2", "http://www.domain2.dt/uri")))),
                List.of(givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.ONE, "USD"))
                        .adId("adId")
                        .creativeId("creativeId")
                        .lineItemId("lineItemId")
                        .dealId("dealId")
                        .html("dealHtml")
                        .advertiser(AdnuntiusAdvertiser.of("legalName", "name"))
                        .destinationUrls(Map.of(
                                "key1", "https://www.domain1.com/uri",
                                "key2", "http://www.domain2.dt/uri"))))));

        final ExtRegsDsa dsa = ExtRegsDsa.of(1, 0, 2, null);
        final BidRequest bidRequest = givenBidRequest(
                request -> request.regs(Regs.builder().ext(ExtRegs.of(null, null, null, dsa)).build()),
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId").build(), identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(
                        mapper.createObjectNode().set("dsa", mapper.createObjectNode()
                                .put("paid", "name")
                                .put("behalf", "name")
                                .put("adrender", 0)),
                        mapper.createObjectNode().set("dsa", mapper.createObjectNode()
                                .put("paid", "legalName")
                                .put("behalf", "legalName")
                                .put("adrender", 0)));

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfCreativeHeightOfSomeAdIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(
                givenAdsUnitWithAds("au1", givenAd(ad -> ad.bid(AdnuntiusBid.of(BigDecimal.TWO, "CUR")))),
                givenAdsUnitWithAds("au2", givenAd(ad -> ad.creativeHeight(null))));

        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().auId("au1").build(), identity()),
                givenBannerImp(ExtImpAdnuntius.builder().auId("au2").build(), identity()));

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
                givenAdsUnitWithAds("au1", givenAd(ad -> ad.bid(AdnuntiusBid.of(BigDecimal.TWO, "CUR")))),
                givenAdsUnitWithAds("au2", givenAd(ad -> ad.creativeWidth(null))));

        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().auId("au1").build(), identity()),
                givenBannerImp(ExtImpAdnuntius.builder().auId("au2").build(), identity()));

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
        final BidderCall<AdnuntiusRequest> httpCall = givenHttpCall(givenAdsUnitWithAds(
                "auId",
                givenAd(ad -> ad
                        .bid(AdnuntiusBid.of(BigDecimal.ONE, "CUR"))
                        .adId("adId")
                        .creativeId("creativeId")
                        .lineItemId("lineItemId")
                        .dealId("dealId")
                        .advertiserDomains(List.of("domain1.com", "domain2.dt")))));

        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(ExtImpAdnuntius.builder().auId("auId").build(), identity()));

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

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer, Imp... imps) {
        return bidRequestCustomizer.apply(BidRequest.builder()).imp(List.of(imps)).build();
    }

    private BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private Imp givenBannerAndNativeImp(ExtImpAdnuntius extImpAdnuntius, UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final Banner banner = Banner.builder().build();
        final Native xNative = Native.builder().request("{}").build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpAdnuntius));
        return impCustomizer.apply(Imp.builder().id("impId").banner(banner).xNative(xNative).ext(ext)).build();
    }

    private Imp givenBannerImp(ExtImpAdnuntius extImpAdnuntius, UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final Banner banner = Banner.builder().build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpAdnuntius));
        return impCustomizer.apply(Imp.builder().id("impId").banner(banner).ext(ext)).build();
    }

    private Imp givenBannerImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBannerImp(ExtImpAdnuntius.builder().build(), impCustomizer);
    }

    private Imp givenNativeImp(ExtImpAdnuntius extImpAdnuntius, UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final Native xNative = Native.builder().request("{}").build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpAdnuntius));
        return impCustomizer.apply(Imp.builder().id("impId").xNative(xNative).ext(ext)).build();
    }

    private Imp givenNativeImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenNativeImp(ExtImpAdnuntius.builder().build(), impCustomizer);
    }

    private BidderCall<AdnuntiusRequest> givenHttpCall(String body) {
        final HttpRequest<AdnuntiusRequest> request = HttpRequest.<AdnuntiusRequest>builder().build();
        final HttpResponse response = HttpResponse.of(200, null, body);
        return BidderCall.succeededHttp(request, response, null);
    }

    private BidderCall<AdnuntiusRequest> givenHttpCall(AdnuntiusAdUnit... adsUnits)
            throws JsonProcessingException {
        return givenHttpCall(mapper.writeValueAsString(AdnuntiusResponse.of(List.of(adsUnits))));
    }

    private AdnuntiusAdUnit givenAdsUnitWithAds(String auId, AdnuntiusAd... ads) {
        return givenAdsUnit(auId, List.of(ads), null, null);
    }

    private AdnuntiusAdUnit givenAdsUnitWithDeals(String auId, AdnuntiusAd... deals) {
        return givenAdsUnit(auId, null, List.of(deals), null);
    }

    private AdnuntiusAdUnit givenBannerAdsUnitWithDealsAndAds(String auId,
                                                              List<AdnuntiusAd> ads,
                                                              List<AdnuntiusAd> deals) {
        return givenAdsUnit(auId, ads, deals, null);
    }

    private AdnuntiusAdUnit givenNativeAdsUnitWithDealsAndAds(String auId,
                                                              List<AdnuntiusAd> ads,
                                                              List<AdnuntiusAd> deals,
                                                              String nativeJson) {
        return givenAdsUnit(auId, ads, deals, nativeJson);
    }

    private AdnuntiusAdUnit givenAdsUnit(String auId,
                                         List<AdnuntiusAd> ads,
                                         List<AdnuntiusAd> deals,
                                         String nativeJson) {

        return AdnuntiusAdUnit.builder()
                .auId(auId)
                .targetId(auId + "-impId")
                .html("html")
                .ads(ads)
                .deals(deals)
                .matchedAdCount(1)
                .nativeJson(nativeJson != null
                        ? jacksonMapper.decodeValue(nativeJson, AdnuntiusNativeRequest.class)
                        : null)
                .build();
    }

    private AdnuntiusAd givenAd(UnaryOperator<AdnuntiusAd.AdnuntiusAdBuilder> customizer) {
        return customizer.apply(AdnuntiusAd.builder()
                .bid(AdnuntiusBid.of(BigDecimal.ONE, "USD"))
                .creativeWidth("21")
                .creativeHeight("9")).build();
    }

    private static ExtDevice givenExtDeviceNoCookies(Boolean noCookies) {
        final ExtDevice extDevice = ExtDevice.empty();
        extDevice.addProperty("noCookies", noCookies != null
                ? BooleanNode.valueOf(noCookies)
                : NullNode.getInstance());
        return extDevice;
    }

    private static String givenExpectedUrl(String url, Integer gdpr, String consent) {
        return buildExpectedUrl(url, gdpr, consent, false);
    }

    private static String givenExpectedUrl(String url, Integer gdpr, Boolean noCookies) {
        return buildExpectedUrl(url, gdpr, null, noCookies);
    }

    private static String givenExpectedUrl(String url, Boolean noCookies) {
        return buildExpectedUrl(url, null, null, noCookies);
    }

    private static String buildExpectedUrl(String url, Integer gdpr, String consent, Boolean noCookies) {
        final StringBuilder expectedUri = new StringBuilder(url + "?format=prebidServer&tzo=-300");
        if (gdpr != null) {
            expectedUri.append("&gdpr=").append(HttpUtil.encodeUrl(gdpr.toString()));
        }
        if (consent != null) {
            expectedUri.append("&consentString=").append(HttpUtil.encodeUrl(consent));
        }
        if (BooleanUtils.isTrue(noCookies)) {
            expectedUri.append("&noCookies=").append(HttpUtil.encodeUrl(noCookies.toString()));
        }
        return expectedUri.toString();
    }
}
