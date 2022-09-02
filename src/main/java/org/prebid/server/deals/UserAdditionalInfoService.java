package org.prebid.server.deals;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.User;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple3;
import org.prebid.server.deals.deviceinfo.DeviceInfoService;
import org.prebid.server.deals.model.DeviceInfo;
import org.prebid.server.deals.model.UserData;
import org.prebid.server.deals.model.UserDetails;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceVendor;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.proto.openrtb.ext.request.ExtGeoVendor;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserTime;
import org.prebid.server.util.ObjectUtil;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Objects;

public class UserAdditionalInfoService {

    private static final Logger logger = LoggerFactory.getLogger(UserAdditionalInfoService.class);

    private final LineItemService lineItemService;
    private final DeviceInfoService deviceInfoService;
    private final GeoLocationService geoLocationService;
    private final UserService userService;
    private final Clock clock;
    private final JacksonMapper mapper;
    private final CriteriaLogManager criteriaLogManager;

    public UserAdditionalInfoService(LineItemService lineItemService,
                                     DeviceInfoService deviceInfoService,
                                     GeoLocationService geoLocationService,
                                     UserService userService,
                                     Clock clock,
                                     JacksonMapper mapper,
                                     CriteriaLogManager criteriaLogManager) {

        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.deviceInfoService = deviceInfoService;
        this.geoLocationService = geoLocationService;
        this.userService = Objects.requireNonNull(userService);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
        this.criteriaLogManager = Objects.requireNonNull(criteriaLogManager);
    }

    public Future<AuctionContext> populate(AuctionContext context) {
        final boolean accountHasDeals = lineItemService.accountHasDeals(context);
        final String accountId = context.getAccount().getId();
        if (!accountHasDeals) {
            criteriaLogManager.log(
                    logger, accountId, "Account %s does not have deals".formatted(accountId), logger::debug);

            return Future.succeededFuture(context);
        }

        final Device device = context.getBidRequest().getDevice();
        final Timeout timeout = context.getTimeout();
        final GeoInfo geoInfo = context.getGeoInfo();

        final CompositeFuture compositeFuture = CompositeFuture.join(
                lookupDeviceInfo(device),
                geoInfo != null ? Future.succeededFuture(geoInfo) : lookupGeoInfo(device, timeout),
                userService.getUserDetails(context, timeout));

        // AsyncResult has atomic nature: its result() method returns null when at least one future fails.
        // So, in handler it is ignored and original CompositeFuture used to process obtained results
        // to avoid explicit casting to CompositeFuture implementation.
        final Promise<Tuple3<DeviceInfo, GeoInfo, UserDetails>> promise = Promise.promise();
        compositeFuture.onComplete(ignored -> handleInfos(compositeFuture, promise, context.getAccount().getId()));
        return promise.future().map(tuple -> enrichAuctionContext(context, tuple));
    }

    private Future<DeviceInfo> lookupDeviceInfo(Device device) {
        return deviceInfoService != null
                ? deviceInfoService.getDeviceInfo(device.getUa())
                : Future.failedFuture("Device info is disabled by configuration");
    }

    private Future<GeoInfo> lookupGeoInfo(Device device, Timeout timeout) {
        return geoLocationService != null
                ? geoLocationService.lookup(ObjectUtils.defaultIfNull(device.getIp(), device.getIpv6()), timeout)
                : Future.failedFuture("Geo location is disabled by configuration");
    }

    private void handleInfos(CompositeFuture compositeFuture,
                             Promise<Tuple3<DeviceInfo, GeoInfo, UserDetails>> resultPromise,
                             String account) {

        DeviceInfo deviceInfo = null;
        GeoInfo geoInfo = null;
        UserDetails userDetails = null;

        for (int i = 0; i < compositeFuture.list().size(); i++) {
            final Object o = compositeFuture.resultAt(i);
            if (o == null) {
                criteriaLogManager.log(
                        logger,
                        account,
                        "Deals processing error: " + compositeFuture.cause(i),
                        logger::warn);
                continue;
            }

            if (o instanceof DeviceInfo) {
                deviceInfo = (DeviceInfo) o;
            } else if (o instanceof GeoInfo) {
                geoInfo = (GeoInfo) o;
            } else if (o instanceof UserDetails) {
                userDetails = (UserDetails) o;
            }
        }

        resultPromise.complete(Tuple3.of(deviceInfo, geoInfo, userDetails));
    }

    private AuctionContext enrichAuctionContext(AuctionContext auctionContext,
                                                Tuple3<DeviceInfo, GeoInfo, UserDetails> tuple) {

        final DeviceInfo deviceInfo = tuple.getLeft();
        final GeoInfo geoInfo = tuple.getMiddle();
        final UserDetails userDetails = tuple.getRight();

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Device originalDevice = bidRequest.getDevice();

        final BidRequest enrichedBidRequest = bidRequest.toBuilder()
                .device(deviceInfo != null || geoInfo != null
                        ? updateDevice(originalDevice, deviceInfo, geoInfo)
                        : originalDevice)
                .user(updateUser(bidRequest.getUser(), userDetails, geoInfo))
                .build();

        return auctionContext.toBuilder()
                .bidRequest(enrichedBidRequest)
                .geoInfo(geoInfo)
                .build();
    }

    private Device updateDevice(Device device, DeviceInfo deviceInfo, GeoInfo geoInfo) {
        final ExtDevice updatedExtDevice =
                fillExtDeviceWith(
                        fillExtDeviceWith(
                                ObjectUtil.getIfNotNull(device, Device::getExt),
                                ObjectUtil.getIfNotNull(deviceInfo, DeviceInfo::getVendor),
                                extDeviceVendorFrom(deviceInfo)),
                        ObjectUtil.getIfNotNull(geoInfo, GeoInfo::getVendor),
                        extDeviceVendorFrom(geoInfo));
        final Geo updatedGeo = updateDeviceGeo(ObjectUtil.getIfNotNull(device, Device::getGeo), geoInfo);

        final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();
        return deviceBuilder
                .geo(updatedGeo)
                .ext(updatedExtDevice)
                .build();
    }

    private ExtDevice fillExtDeviceWith(ExtDevice extDevice, String vendor, ExtDeviceVendor extDeviceVendor) {
        if (extDeviceVendor.equals(ExtDeviceVendor.EMPTY)) {
            return extDevice;
        }

        final ExtDevice effectiveExtDevice = extDevice != null ? extDevice : ExtDevice.empty();
        effectiveExtDevice.addProperty(vendor, mapper.mapper().valueToTree(extDeviceVendor));

        return effectiveExtDevice;
    }

    private static ExtDeviceVendor extDeviceVendorFrom(DeviceInfo deviceInfo) {
        return deviceInfo != null
                ? ExtDeviceVendor.builder()
                .type(deviceInfo.getDeviceTypeRaw())
                .osfamily(null)
                .os(deviceInfo.getOs())
                .osver(deviceInfo.getOsVersion())
                .browser(deviceInfo.getBrowser())
                .browserver(deviceInfo.getBrowserVersion())
                .make(deviceInfo.getManufacturer())
                .model(deviceInfo.getModel())
                .language(deviceInfo.getLanguage())
                .carrier(deviceInfo.getCarrier())
                .build()
                : ExtDeviceVendor.EMPTY;
    }

    private static ExtDeviceVendor extDeviceVendorFrom(GeoInfo geoInfo) {
        return geoInfo != null
                ? ExtDeviceVendor.builder()
                .connspeed(geoInfo.getConnectionSpeed())
                .build()
                : ExtDeviceVendor.EMPTY;
    }

    private Geo updateDeviceGeo(Geo geo, GeoInfo geoInfo) {
        if (geoInfo == null) {
            return geo;
        }

        final ExtGeo updatedExtGeo = fillExtGeoWith(
                ObjectUtil.getIfNotNull(geo, Geo::getExt),
                geoInfo.getVendor(),
                extGeoVendorFrom(geoInfo));

        final Geo.GeoBuilder geoBuilder = geo != null ? geo.toBuilder() : Geo.builder();
        return geoBuilder
                .region(geoInfo.getRegion())
                .metro(geoInfo.getMetroGoogle())
                .lat(geoInfo.getLat())
                .lon(geoInfo.getLon())
                .ext(updatedExtGeo)
                .build();
    }

    private ExtGeo fillExtGeoWith(ExtGeo extGeo, String vendor, ExtGeoVendor extGeoVendor) {
        if (extGeoVendor.equals(ExtGeoVendor.EMPTY)) {
            return extGeo;
        }

        final ExtGeo effectiveExtGeo = extGeo != null ? extGeo : ExtGeo.of();
        effectiveExtGeo.addProperty(vendor, mapper.mapper().valueToTree(extGeoVendor));

        return effectiveExtGeo;
    }

    private static ExtGeoVendor extGeoVendorFrom(GeoInfo geoInfo) {
        return ExtGeoVendor.builder()
                .continent(geoInfo.getContinent())
                .country(geoInfo.getCountry())
                .region(geoInfo.getRegionCode())
                .metro(geoInfo.getMetroNielsen())
                .city(geoInfo.getCity())
                .zip(geoInfo.getZip())
                .build();
    }

    private User updateUser(User user, UserDetails userDetails, GeoInfo geoInfo) {
        final User.UserBuilder userBuilder = user != null ? user.toBuilder() : User.builder();
        return userBuilder
                .data(userDetails != null ? makeData(userDetails) : null)
                .ext(updateExtUser(ObjectUtil.getIfNotNull(user, User::getExt), userDetails, geoInfo))
                .build();
    }

    private static List<Data> makeData(UserDetails userDetails) {
        final List<UserData> userData = userDetails.getUserData();
        return userData != null
                ? userData.stream()
                .map(userDataElement -> Data.builder()
                        .id(userDataElement.getName())
                        .segment(makeSegments(userDataElement.getSegment()))
                        .build())
                .toList()
                : null;
    }

    private static List<Segment> makeSegments(List<org.prebid.server.deals.model.Segment> segments) {
        return segments != null
                ? segments.stream()
                .map(segment -> Segment.builder().id(segment.getId()).build())
                .toList()
                : null;
    }

    private ExtUser updateExtUser(ExtUser extUser, UserDetails userDetails, GeoInfo geoInfo) {
        final ExtUser.ExtUserBuilder extUserBuilder = extUser != null ? extUser.toBuilder() : ExtUser.builder();
        return extUserBuilder
                .fcapIds(ObjectUtils.defaultIfNull(
                        resolveFcapIds(userDetails),
                        ObjectUtil.getIfNotNull(extUser, ExtUser::getFcapIds)))
                .time(resolveExtUserTime(geoInfo))
                .build();
    }

    private static List<String> resolveFcapIds(UserDetails userDetails) {
        return userDetails != null
                // Indicate that the call to User Data Store has been made successfully even if the user is not frequency
                // capped
                ? ListUtils.emptyIfNull(userDetails.getFcapIds())
                // otherwise leave cappedIds null to indicate that call to User Data Store failed
                : null;
    }

    private ExtUserTime resolveExtUserTime(GeoInfo geoInfo) {
        final ZoneId timeZone = ObjectUtils.firstNonNull(
                ObjectUtil.getIfNotNull(geoInfo, GeoInfo::getTimeZone),
                clock.getZone());

        final ZonedDateTime dateTime = ZonedDateTime.now(clock).withZoneSameInstant(timeZone);

        return ExtUserTime.of(
                dateTime.getDayOfWeek().get(WeekFields.SUNDAY_START.dayOfWeek()),
                dateTime.getHour());
    }
}
