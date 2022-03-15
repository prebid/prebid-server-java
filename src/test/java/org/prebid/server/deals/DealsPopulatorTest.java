package org.prebid.server.deals;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.deviceinfo.DeviceInfoService;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.DeviceInfo;
import org.prebid.server.deals.model.MatchLineItemsResult;
import org.prebid.server.deals.model.UserData;
import org.prebid.server.deals.model.UserDetails;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.LineItemSize;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceVendor;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.proto.openrtb.ext.request.ExtGeoVendor;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserTime;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class DealsPopulatorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private LineItemService lineItemService;

    @Mock
    private DeviceInfoService deviceInfoService;

    @Mock
    private GeoLocationService geoLocationService;

    @Mock
    private UserService userService;

    @Mock
    private CriteriaLogManager criteriaLogManager;

    private DealsPopulator dealsPopulator;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2019-10-10T00:01:00Z"), ZoneOffset.UTC);

    @Before
    public void setUp() {
        dealsPopulator = new DealsPopulator(
                lineItemService,
                deviceInfoService,
                geoLocationService,
                userService,
                CLOCK,
                jacksonMapper,
                criteriaLogManager);
    }

    @Test
    public void populateDealsInfoShouldReturnOriginalContextIfAccountHasNoDeals() {
        // given
        given(lineItemService.accountHasDeals(any())).willReturn(false);

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(givenAccount(identity()))
                .bidRequest(givenBidRequest(identity()))
                .build();

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result).isSameAs(auctionContext);
    }

    @Test
    public void populateDealsInfoShouldPopulateDevice() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(deviceInfoService.getDeviceInfo(any()))
                .willReturn(Future.succeededFuture(DeviceInfo.builder()
                        .vendor("vendor")
                        .deviceTypeRaw("mobile")
                        .os("os")
                        .osVersion("osVersion")
                        .browser("browser")
                        .browserVersion("browserVersion")
                        .language("ENG")
                        .carrier("AT&T")
                        .manufacturer("Apple")
                        .model("iPhone 8")
                        .build()));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder()
                        .ip("ip")
                        .ua("ua")
                        .ext(ExtDevice.of(null, ExtDevicePrebid.of(ExtDeviceInt.of(640, 480))))
                        .build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        final ExtDevice expectedExtDevice = ExtDevice.of(null, ExtDevicePrebid.of(ExtDeviceInt.of(640, 480)));
        expectedExtDevice.addProperty("vendor", mapper.valueToTree(ExtDeviceVendor.builder()
                .type("mobile")
                .os("os")
                .osver("osVersion")
                .browser("browser")
                .browserver("browserVersion")
                .make("Apple")
                .model("iPhone 8")
                .language("ENG")
                .carrier("AT&T")
                .build()));
        assertThat(result.getBidRequest().getDevice()).isEqualTo(Device.builder()
                .ip("ip")
                .ua("ua")
                .ext(expectedExtDevice)
                .build());
    }

    @Test
    public void populateDealsInfoShouldPopulateDeviceGeo() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(geoLocationService.lookup(any(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder()
                        .vendor("vendor")
                        .continent("continent")
                        .country("country")
                        .region("region")
                        .regionCode(1)
                        .city("city")
                        .metroGoogle("metroGoogle")
                        .metroNielsen(516)
                        .zip("12345")
                        .connectionSpeed("broadband")
                        .lat(11.11F)
                        .lon(22.22F)
                        .build()));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder()
                        .geo(Geo.builder().zip("zip").build())
                        .build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        final ExtGeo expectedExtGeo = ExtGeo.of();
        expectedExtGeo.addProperty("vendor", mapper.valueToTree(ExtGeoVendor.builder()
                .continent("continent")
                .country("country")
                .region(1)
                .metro(516)
                .city("city")
                .zip("12345")
                .build()));
        final ExtDevice expectedDevice = ExtDevice.of(null, null);
        expectedDevice.addProperty("vendor", mapper.valueToTree(ExtDeviceVendor.builder()
                .connspeed("broadband")
                .build()));
        assertThat(result.getBidRequest().getDevice()).isEqualTo(Device.builder()
                .geo(Geo.builder()
                        .zip("zip")
                        .region("region")
                        .metro("metroGoogle")
                        .lat(11.11F)
                        .lon(22.22F)
                        .ext(expectedExtGeo)
                        .build())
                .ext(expectedDevice)
                .build());
    }

    @Test
    public void populateDealsInfoShouldPopulateUserWithUserDetails() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(userService.getUserDetails(any(), any())).willReturn(Future.succeededFuture(
                UserDetails.of(singletonList(UserData.of(null, "rubicon",
                                singletonList(org.prebid.server.deals.model.Segment.of("segmentId")))),
                        singletonList("fcapId"))));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().ip("ip").ua("ua").build())
                .user(User.builder()
                        .ext(ExtUser.builder().consent("consent").build())
                        .build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result.getBidRequest().getUser()).isEqualTo(User.builder()
                .data(singletonList(Data.builder().id("rubicon")
                        .segment(singletonList(Segment.builder().id("segmentId").build())).build()))
                .ext(ExtUser.builder()
                        .consent("consent")
                        .fcapIds(singletonList("fcapId"))
                        .time(ExtUserTime.of(5, 0))
                        .build())
                .build());
    }

    @Test
    public void populateDealsInfoShouldUseForGeoLocationIpV6IfIpV4IsNotDefined() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().ipv6("ipv6").build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        dealsPopulator.populate(auctionContext).result();

        // then
        verify(geoLocationService).lookup(eq("ipv6"), any());
    }

    @Test
    public void populateDealsInfoShouldPopulateUserExtWithGeoInfo() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(geoLocationService.lookup(any(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder()
                        .vendor("vendor")
                        .timeZone(ZoneId.of("America/Los_Angeles"))
                        .build()));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().ip("ip").ua("ua").build())
                .user(User.builder()
                        .ext(ExtUser.builder().consent("consent").build())
                        .build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result.getBidRequest().getUser()).isEqualTo(User.builder()
                .ext(ExtUser.builder()
                        .consent("consent")
                        .time(ExtUserTime.of(4, 17))
                        .build())
                .build());
    }

    @Test
    public void populateDealsInfoShouldPopulateUserExtWithGeoInfoIfTimeZoneIsMissing() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(geoLocationService.lookup(any(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder()
                        .vendor("vendor")
                        .build()));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().ip("ip").ua("ua").build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result.getBidRequest().getUser()).isEqualTo(User.builder()
                .ext(ExtUser.builder()
                        .time(ExtUserTime.of(5, 0))
                        .build())
                .build());
    }

    @Test
    public void populateDealsInfoShouldPopulateUserWithEmptyCappedIds() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(userService.getUserDetails(any(), any()))
                .willReturn(Future.succeededFuture(UserDetails.of(null, null)));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().ip("ip").ua("ua").build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result.getBidRequest().getUser()).isEqualTo(User.builder()
                .ext(ExtUser.builder()
                        .fcapIds(emptyList())
                        .time(ExtUserTime.of(5, 0))
                        .build())
                .build());
    }

    @Test
    public void populateDealsInfoShouldPopulateUserWithNullCappedIds() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().ip("ip").ua("ua").build())
                .user(User.builder()
                        .ext(ExtUser.builder().consent("consent").build())
                        .build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result.getBidRequest().getUser()).isEqualTo(User.builder()
                .ext(ExtUser.builder()
                        .consent("consent")
                        .time(ExtUserTime.of(5, 0))
                        .build())
                .build());
    }

    @Test
    public void populateDealsInfoShouldEnrichImpWithDeals() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(lineItemService.findMatchingLineItems(any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder()
                                .lineItemId("lineItemId")
                                .extLineItemId("extLineItemId")
                                .source("bidder")
                                .dealId("dealId")
                                .build(),
                        null, null, ZonedDateTime.now(CLOCK)))));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(Imp.builder()
                        .pmp(Pmp.builder()
                                .deals(singletonList(Deal.builder().id("existingDealId").build()))
                                .build())
                        .build())));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(singletonList(result))
                .extracting(AuctionContext::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .flatExtracting(Pmp::getDeals)
                .containsOnly(
                        Deal.builder().id("existingDealId").build(),
                        Deal.builder()
                                .id("dealId")
                                .ext(mapper.valueToTree(
                                        ExtDeal.of(ExtDealLine.of("lineItemId", "extLineItemId", null, "bidder"))))
                                .build());
    }

    @Test
    public void populateDealsInfoShouldEnrichImpWithDealsAndAddLineItemSizesIfSizesIntersectionMatched() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(lineItemService.findMatchingLineItems(any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder().sizes(singletonList(LineItemSize.of(200, 20))).build(), null, null,
                        ZonedDateTime.now(CLOCK)))));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(
                                        Format.builder().w(100).h(10).build(),
                                        Format.builder().w(200).h(20).build()))
                                .build())
                        .build())));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(singletonList(result))
                .extracting(AuctionContext::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .flatExtracting(Pmp::getDeals)
                .extracting(Deal::getExt)
                .extracting(node -> mapper.treeToValue(node, ExtDeal.class))
                .containsOnly(ExtDeal.of(
                        ExtDealLine.of(null, null, singletonList(Format.builder().w(200).h(20).build()), null)));
    }

    @Test
    public void populateDealsInfoShouldEnrichImpWithDealsAndNotAddLineItemSizesIfSizesIntersectionNotMatched() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(lineItemService.findMatchingLineItems(any(), any()))
                .willReturn(MatchLineItemsResult.of(singletonList(LineItem.of(
                        LineItemMetaData.builder().sizes(singletonList(LineItemSize.of(200, 20))).build(), null, null,
                        ZonedDateTime.now(CLOCK)))));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .device(Device.builder().ip("ip").ua("ua").build())
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(100).h(10).build()))
                                .build())
                        .build())));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(singletonList(result))
                .extracting(AuctionContext::getBidRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getPmp)
                .flatExtracting(Pmp::getDeals)
                .extracting(Deal::getExt)
                .extracting(node -> mapper.treeToValue(node, ExtDeal.class))
                .containsOnly(ExtDeal.of(ExtDealLine.of(null, null, null, null)));
    }

    @Test
    public void populateDealsInfoShouldNotPopulateDeviceGeoIfGeolocationServiceIsNotDefined() {
        // given
        dealsPopulator = new DealsPopulator(
                lineItemService,
                deviceInfoService,
                null,
                userService,
                CLOCK,
                jacksonMapper,
                criteriaLogManager);
        givenResultForLineItemDeviceGeoUserServices();

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().build()));

        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result.getBidRequest().getDevice().getGeo()).isNull();
    }

    @Test
    public void populateDealsInfoShouldCreateExtDeviceIfDeviceInfoIsNotEmptyAndExtDidNotExistBefore() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(geoLocationService.lookup(any(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder()
                        .vendor("geoVendor")
                        .build()));

        given(deviceInfoService.getDeviceInfo(any()))
                .willReturn(Future.succeededFuture(DeviceInfo.builder().vendor("deviceVendor")
                        .browser("browser").build()));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().build()));

        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();
        final ExtDevice expectedExtDevice = ExtDevice.of(null, null);
        expectedExtDevice.addProperty("deviceVendor",
                mapper.valueToTree(ExtDeviceVendor.builder().browser("browser").build()));
        // then
        assertThat(result.getBidRequest().getDevice().getExt()).isEqualTo(expectedExtDevice);
    }

    @Test
    public void populateDealsInfoShouldCreateExtDeviceIfGeoInfoIsNotEmptyAndExtDidNotExistBefore() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        given(geoLocationService.lookup(any(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder()
                        .vendor("geoVendor")
                        .connectionSpeed("100")
                        .continent("continent")
                        .build()));

        given(deviceInfoService.getDeviceInfo(any()))
                .willReturn(Future.succeededFuture(DeviceInfo.builder()
                        .vendor("deviceVendor").build()));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().build()));

        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        final ExtDevice expectedExtDevice = ExtDevice.of(null, null);
        expectedExtDevice.addProperty("geoVendor",
                mapper.valueToTree(ExtDeviceVendor.builder().connspeed("100").build()));
        assertThat(result.getBidRequest().getDevice().getExt())
                .isEqualTo(expectedExtDevice);
    }

    @Test
    public void populateDealsInfoShouldUseGeoInfoFromContext() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().build()));
        final GeoInfo geoInfo = GeoInfo.builder()
                .vendor("vendor")
                .city("city")
                .build();
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity())).toBuilder()
                .geoInfo(geoInfo)
                .build();

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        final ExtGeo expectedExtGeo = ExtGeo.of();
        expectedExtGeo.addProperty("vendor", mapper.valueToTree(ExtGeoVendor.builder().city("city").build()));
        assertThat(result.getBidRequest().getDevice()).isEqualTo(Device.builder()
                .geo(Geo.builder().ext(expectedExtGeo).build())
                .build());
        verifyNoInteractions(geoLocationService);
        assertThat(result.getGeoInfo()).isSameAs(geoInfo);
    }

    @Test
    public void populateDealsInfoShouldStoreGeoInfoInContext() {
        // given
        givenResultForLineItemDeviceGeoUserServices();

        final GeoInfo geoInfo = GeoInfo.builder()
                .vendor("vendor")
                .city("city")
                .build();
        given(geoLocationService.lookup(any(), any())).willReturn(Future.succeededFuture(geoInfo));

        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .device(Device.builder().build()));
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, givenAccount(identity()));

        // when
        final AuctionContext result = dealsPopulator.populate(auctionContext).result();

        // then
        assertThat(result.getGeoInfo()).isSameAs(geoInfo);
    }

    private void givenResultForLineItemDeviceGeoUserServices() {
        given(lineItemService.accountHasDeals(any())).willReturn(true);

        given(deviceInfoService.getDeviceInfo(any()))
                .willReturn(Future.failedFuture("deviceInfoService error"));
        given(geoLocationService.lookup(any(), any()))
                .willReturn(Future.failedFuture("geoLocationService error"));
        given(userService.getUserDetails(any(), any()))
                .willReturn(Future.failedFuture("userService error"));

        given(lineItemService.findMatchingLineItems(any(), any()))
                .willReturn(MatchLineItemsResult.of(emptyList()));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> customizer) {
        return customizer.apply(BidRequest.builder()).build();
    }

    private static Account givenAccount(Function<Account.AccountBuilder, Account.AccountBuilder> customizer) {
        return customizer.apply(Account.builder().id("accountId")).build();
    }

    private static AuctionContext givenAuctionContext(BidRequest bidRequest, Account account) {
        return AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .debugWarnings(new ArrayList<>())
                .build();
    }
}
