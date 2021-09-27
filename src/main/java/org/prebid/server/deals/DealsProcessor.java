package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple3;
import org.prebid.server.deals.deviceinfo.DeviceInfoService;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.DeviceInfo;
import org.prebid.server.deals.model.MatchLineItemsResult;
import org.prebid.server.deals.model.UserData;
import org.prebid.server.deals.model.UserDetails;
import org.prebid.server.deals.proto.LineItemSize;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceVendor;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.proto.openrtb.ext.request.ExtGeoVendor;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserTime;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal.Category;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.StreamUtil;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Role as a dispatcher between request factory and all other PG components.
 */
public class DealsProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DealsProcessor.class);

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";
    private static final String DEALS_ONLY = "dealsonly";

    private final LineItemService lineItemService;
    private final DeviceInfoService deviceInfoService;
    private final GeoLocationService geoLocationService;
    private final UserService userService;
    private final Clock clock;
    private final JacksonMapper mapper;
    private final CriteriaLogManager criteriaLogManager;

    public DealsProcessor(LineItemService lineItemService,
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

    /**
     * Returns new {@link AuctionContext} with populated deals information like device info,
     * geo-location data, user frequency capping info and deal IDs for corresponding impressions.
     * <p>
     * If account doesn't need additional deals processing, will return the same given {@link AuctionContext}.
     */
    public Future<AuctionContext> populateDealsInfo(AuctionContext context) {
        final boolean accountHasDeals = lineItemService.accountHasDeals(context);
        final String accountId = context.getAccount().getId();
        if (!accountHasDeals) {
            criteriaLogManager.log(logger, accountId, String.format("Account %s does not have deals", accountId),
                    logger::debug);
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
        compositeFuture.setHandler(ignored -> handleDealsInfo(compositeFuture, promise, context.getAccount().getId()));
        return promise.future()
                .map((Tuple3<DeviceInfo, GeoInfo, UserDetails> tuple) ->
                        enrichAuctionContext(context, tuple.getLeft(), tuple.getMiddle(), tuple.getRight()))
                .map(this::matchAndPopulateDeals);
    }

    /**
     * Finds deals only bidders without matched deals and removes them from {@link Imp}.
     * If {@link Imp} has only deals only bidders without deals, null will be returned.
     */
    public static Imp removeDealsOnlyBiddersWithoutDeals(Imp imp, AuctionContext auctionContext) {
        final Set<String> dealsOnlyBiddersToRemove = getDealsOnlyBiddersToRemove(imp);
        Imp resolvedImp = imp;
        if (CollectionUtils.isNotEmpty(dealsOnlyBiddersToRemove)) {
            final ObjectNode extImp = removeBidders(imp, dealsOnlyBiddersToRemove);
            resolvedImp = imp.toBuilder().ext(extImp).build();
            if (hasBidder(resolvedImp)) {
                logBidderOrImpExclusion(auctionContext, imp, dealsOnlyBiddersToRemove, "No Line Items from "
                        + "bidders %s matching imp with id %s and ready to serve. Removing impression from request"
                        + " to these bidders because dealsonly flag is on for them.");
            } else {
                logBidderOrImpExclusion(auctionContext, imp, dealsOnlyBiddersToRemove, "No Line Items from "
                        + "bidders %s matching imp with id %s and ready to serve. Removing imp from requests to all"
                        + " bidders because dealsonly flag is on for these bidders and no other valid bidders in imp.");
                resolvedImp = null;
            }
        }

        return resolvedImp;
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

    /**
     * Handles obtained {@link DeviceInfo}, {@link GeoInfo}, {@link UserDetails} results from {@link CompositeFuture}.
     */
    private void handleDealsInfo(CompositeFuture compositeFuture,
                                 Promise<Tuple3<DeviceInfo, GeoInfo, UserDetails>> resultPromise, String account) {
        DeviceInfo deviceInfo = null;
        GeoInfo geoInfo = null;
        UserDetails userDetails = null;

        for (int i = 0; i < compositeFuture.list().size(); i++) {
            final Object o = compositeFuture.resultAt(i);
            if (o == null) {
                criteriaLogManager.log(logger, account, String.format("Deals processing error: %s",
                        compositeFuture.cause(i)), logger::warn);
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

    /**
     * Stores information from {@link DeviceInfoService}, {@link GeoLocationService} and {@link UserService}
     * to {@link BidRequest} to make it available during targeting evaluation.
     */
    private AuctionContext enrichAuctionContext(
            AuctionContext auctionContext, DeviceInfo deviceInfo, GeoInfo geoInfo, UserDetails userDetails) {

        final BidRequest bidRequest = auctionContext.getBidRequest();

        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();

        final User updatedUser = updateUser(bidRequest.getUser(), userDetails, geoInfo);
        requestBuilder.user(updatedUser);

        if (deviceInfo != null || geoInfo != null) {
            final Device updatedDevice = updateDevice(bidRequest.getDevice(), deviceInfo, geoInfo);
            requestBuilder.device(updatedDevice);
        }

        return auctionContext.toBuilder()
                .bidRequest(requestBuilder.build())
                .geoInfo(geoInfo)
                .build();
    }

    /**
     * Returns {@link Device} populated with {@link DeviceInfo} and {@link GeoInfo} data.
     */
    private Device updateDevice(Device device, DeviceInfo deviceInfo, GeoInfo geoInfo) {
        final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

        ExtDevice updatedExtDevice = ObjectUtil.getIfNotNull(device, Device::getExt);
        if (deviceInfo != null) {
            final ExtDeviceVendor extDeviceVendor = ExtDeviceVendor.builder()
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
                    .build();

            if (!extDeviceVendor.equals(ExtDeviceVendor.EMPTY)) {
                if (updatedExtDevice == null) {
                    updatedExtDevice = ExtDevice.empty();
                    deviceBuilder.ext(updatedExtDevice);
                }
                updatedExtDevice.addProperty(deviceInfo.getVendor(), mapper.mapper().valueToTree(extDeviceVendor));
            }
        }

        if (geoInfo != null) {
            final Geo updatedDeviceGeo = updateDeviceGeo(device, geoInfo);
            deviceBuilder.geo(updatedDeviceGeo);

            if (geoInfo.getConnectionSpeed() != null) {
                final ExtDeviceVendor extDeviceVendor = ExtDeviceVendor.builder()
                        .connspeed(geoInfo.getConnectionSpeed())
                        .build();
                if (updatedExtDevice == null) {
                    updatedExtDevice = ExtDevice.empty();
                    deviceBuilder.ext(updatedExtDevice);
                }
                updatedExtDevice.addProperty(geoInfo.getVendor(), mapper.mapper().valueToTree(extDeviceVendor));
            }
        }

        return deviceBuilder.build();
    }

    /**
     * Returns {@link Geo} populated with {@link GeoInfo} data.
     */
    private Geo updateDeviceGeo(Device device, GeoInfo geoInfo) {
        final String geoInfoVendor = geoInfo.getVendor();

        final Geo geo = ObjectUtil.getIfNotNull(device, Device::getGeo);
        final ExtGeo extGeo = ObjectUtil.getIfNotNull(geo, Geo::getExt);
        final JsonNode extGeoVendorNode = ObjectUtil.getIfNotNull(extGeo, node -> node.getProperty(geoInfoVendor));
        final ExtGeoVendor extGeoVendor = parseExt(extGeoVendorNode, ExtGeoVendor.class);

        final ExtGeo updatedExtGeoNode = extGeo != null ? extGeo : ExtGeo.of();
        if (StringUtils.isNotBlank(geoInfo.getContinent())
                || StringUtils.isNotBlank(geoInfo.getCountry())
                || geoInfo.getRegionCode() != null
                || geoInfo.getMetroNielsen() != null
                || StringUtils.isNotBlank(geoInfo.getCity())
                || StringUtils.isNotBlank(geoInfo.getZip())) {

            final ExtGeoVendor.ExtGeoVendorBuilder extGeoVendorBuilder = extGeoVendor != null
                    ? extGeoVendor.toBuilder()
                    : ExtGeoVendor.builder();

            final ExtGeoVendor updatedExtGeoVendor = extGeoVendorBuilder
                    .continent(geoInfo.getContinent())
                    .country(geoInfo.getCountry())
                    .region(geoInfo.getRegionCode())
                    .metro(geoInfo.getMetroNielsen())
                    .city(geoInfo.getCity())
                    .zip(geoInfo.getZip())
                    .build();
            updatedExtGeoNode.addProperty(geoInfoVendor, mapper.mapper().valueToTree(updatedExtGeoVendor));
        }

        final Geo.GeoBuilder geoBuilder = geo != null ? geo.toBuilder() : Geo.builder();
        return geoBuilder
                .region(geoInfo.getRegion())
                .metro(geoInfo.getMetroGoogle())
                .lat(geoInfo.getLat())
                .lon(geoInfo.getLon())
                .ext(updatedExtGeoNode)
                .build();
    }

    /**
     * Returns {@link User} populated with {@link UserDetails} data.
     */
    private User updateUser(User user, UserDetails userDetails, GeoInfo geoInfo) {
        final ExtUser extUser = ObjectUtil.getIfNotNull(user, User::getExt);
        final ExtUser.ExtUserBuilder extUserBuilder = extUser != null ? extUser.toBuilder() : ExtUser.builder();

        updateUserExtWithUserDetails(extUserBuilder, userDetails);
        updateUserExtWithGeoInfo(extUserBuilder, geoInfo);

        final User.UserBuilder userBuilder = user != null ? user.toBuilder() : User.builder();
        return userBuilder
                .data(userDetails != null ? makeUserData(userDetails) : null)
                .ext(extUserBuilder.build())
                .build();
    }

    private void updateUserExtWithUserDetails(ExtUser.ExtUserBuilder extUserBuilder, UserDetails userDetails) {
        if (userDetails != null) {
            // Indicate that the call to User Data Store has been made successfully even if the user is not frequency
            // capped
            extUserBuilder.fcapIds(ListUtils.emptyIfNull(userDetails.getFcapIds()));
        } // otherwise leave cappedIds null to indicate that call to User Data Store failed
    }

    private void updateUserExtWithGeoInfo(ExtUser.ExtUserBuilder extUserBuilder, GeoInfo geoInfo) {
        final ZoneId timeZone = ObjectUtils.firstNonNull(
                ObjectUtil.getIfNotNull(geoInfo, GeoInfo::getTimeZone), clock.getZone());

        final ZonedDateTime dateTime = ZonedDateTime.now(clock).withZoneSameInstant(timeZone);

        extUserBuilder.time(ExtUserTime.of(
                dateTime.getDayOfWeek().get(WeekFields.SUNDAY_START.dayOfWeek()),
                dateTime.getHour()));
    }

    /**
     * Makes {@link List<Data>} from {@link UserDetails}.
     */
    private List<Data> makeUserData(UserDetails userDetails) {
        final List<UserData> userData = userDetails.getUserData();
        return userData != null
                ? userData.stream()
                .map(userDataElement -> Data.builder()
                        .id(userDataElement.getName())
                        .segment(makeSegments(userDataElement.getSegment())).build())
                .collect(Collectors.toList())
                : null;
    }

    /**
     * Makes {@link List<Segment>} from {@link List<org.prebid.server.deals.model.Segment>}.
     */
    private List<Segment> makeSegments(List<org.prebid.server.deals.model.Segment> segments) {
        return segments != null
                ? segments.stream()
                .map(segment -> Segment.builder().id(segment.getId()).build())
                .collect(Collectors.toList())
                : null;
    }

    /**
     * Transforms {@link ObjectNode} to the object of the given {@link Class} type or returns null if error occurred.
     */
    private <T> T parseExt(JsonNode node, Class<T> clazz) {
        if (node == null) {
            return null;
        }
        try {
            return mapper.mapper().treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Fetches {@link MatchLineItemsResult} for each {@link Imp} and enriches it with {@link Deal}s.
     */
    private AuctionContext matchAndPopulateDeals(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final List<Imp> updatedImps = new ArrayList<>();
        boolean isImpsUpdated = false;

        for (Imp imp : bidRequest.getImp()) {
            final MatchLineItemsResult matchResult = lineItemService.findMatchingLineItems(auctionContext, imp);
            final List<LineItem> lineItems = matchResult.getLineItems();

            lineItems.forEach(lineItem -> criteriaLogManager.log(logger, lineItem.getAccountId(), lineItem.getSource(),
                    lineItem.getLineItemId(),
                    String.format("LineItem %s is ready to be served", lineItem.getLineItemId()), logger::debug));

            final Imp updatedImp = lineItems.isEmpty() ? imp : enrichImpWithDeals(imp, lineItems);
            isImpsUpdated |= imp != updatedImp;

            updatedImps.add(updatedImp);
        }

        final AuctionContext result;
        if (!isImpsUpdated) {
            result = auctionContext;
        } else {
            final BidRequest updatedBidRequest = bidRequest.toBuilder().imp(updatedImps).build();
            result = auctionContext.toBuilder().bidRequest(updatedBidRequest).build();
        }
        return result;
    }

    /**
     * Populates request.imp[].pmp object:
     * <p>
     * - injects dealIds from selected {@link LineItem}s to corresponding request.imp[].pmp.deals[].id.
     * <p>
     * - stores {@link LineItem} information in request.imp[].pmp.deals[].ext.line object.
     */
    private Imp enrichImpWithDeals(Imp imp, List<LineItem> lineItems) {
        final List<Deal> deals = lineItems.stream()
                .map(lineItem -> toDeal(imp, lineItem))
                .collect(Collectors.toList());

        return impWithPopulatedDeals(imp, deals);
    }

    /**
     * Creates {@link Deal} from the given {@link LineItem}.
     */
    private Deal toDeal(Imp imp, LineItem lineItem) {
        return Deal.builder()
                .id(lineItem.getDealId())
                .ext(mapper.mapper().valueToTree(ExtDeal.of(toExtDealLine(imp, lineItem))))
                .build();
    }

    private static ExtDealLine toExtDealLine(Imp imp, LineItem lineItem) {
        final List<Format> formats = ObjectUtil.getIfNotNull(imp.getBanner(), Banner::getFormat);
        final List<LineItemSize> lineItemSizes = lineItem.getSizes();

        final List<Format> lineSizes;
        if (CollectionUtils.isNotEmpty(formats) && CollectionUtils.isNotEmpty(lineItemSizes)) {
            final List<Format> matchedSizes = lineItemSizes.stream()
                    .filter(size -> formatsContainLineItemSize(formats, size))
                    .map(size -> Format.builder().w(size.getW()).h(size.getH()).build())
                    .collect(Collectors.toList());
            lineSizes = CollectionUtils.isNotEmpty(matchedSizes) ? matchedSizes : null;
        } else {
            lineSizes = null;
        }

        return ExtDealLine.of(lineItem.getLineItemId(), lineItem.getExtLineItemId(), lineSizes, lineItem.getSource());
    }

    /**
     * Returns true if the given {@link LineItemSize} is found in a list of imp.banner {@link Format}s.
     */
    private static boolean formatsContainLineItemSize(List<Format> formats, LineItemSize lineItemSize) {
        return formats.stream()
                .anyMatch(format -> Objects.equals(format.getW(), lineItemSize.getW())
                        && Objects.equals(format.getH(), lineItemSize.getH()));
    }

    /**
     * Returns {@link Imp} with populated {@link Deal}s.
     */
    private static Imp impWithPopulatedDeals(Imp imp, List<Deal> deals) {
        final Pmp pmp = imp.getPmp();
        final List<Deal> existingDeals = ListUtils.emptyIfNull(pmp != null ? pmp.getDeals() : null);

        final List<Deal> combinedDeals = Stream.concat(existingDeals.stream(), deals.stream())
                .collect(Collectors.toList());

        final Pmp.PmpBuilder pmpBuilder = pmp != null ? pmp.toBuilder() : Pmp.builder();
        final Pmp updatedPmp = pmpBuilder.deals(combinedDeals).build();
        return imp.toBuilder().pmp(updatedPmp).build();
    }

    /**
     * Returns {@link Set<String>} of bidder names, which should be removed from {@link Imp}.
     */
    private static Set<String> getDealsOnlyBiddersToRemove(Imp imp) {
        final Pmp pmp = imp.getPmp();
        final List<Deal> deals = pmp == null ? null : pmp.getDeals();
        return CollectionUtils.isEmpty(deals)
                ? findDealsOnlyBidders(imp)
                : Collections.emptySet();
    }

    /**
     * Returns {@link Set<String>} of valid bidder names, which has dealsOnly parameter with true value.
     */
    private static Set<String> findDealsOnlyBidders(Imp imp) {
        return StreamUtil.asStream(bidderNodesFromImp(imp))
                .filter(bidderNode -> isDealsOnlyBidder(bidderNode.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static Iterator<Map.Entry<String, JsonNode>> bidderNodesFromImp(Imp imp) {
        final JsonNode extPrebidBidder = imp.getExt().get(PREBID_EXT).get(BIDDER_EXT);

        return extPrebidBidder != null ? extPrebidBidder.fields() : Collections.emptyIterator();
    }

    /**
     * Checks if {@link Imp} has at least one valid bidder.
     */
    private static boolean hasBidder(Imp imp) {
        return bidderNodesFromImp(imp).hasNext();
    }

    /**
     * Checks if bidder is deals only.
     */
    private static boolean isDealsOnlyBidder(JsonNode bidder) {
        final JsonNode dealsOnlyNode = bidder.get(DEALS_ONLY);
        return dealsOnlyNode != null && dealsOnlyNode.isBoolean() && dealsOnlyNode.asBoolean();
    }

    /**
     * Removes bidders from {@link Imp}.
     */
    private static ObjectNode removeBidders(Imp imp, Set<String> bidders) {
        final ObjectNode modifiedExt = imp.getExt().deepCopy();
        final ObjectNode extPrebidBidder = (ObjectNode) modifiedExt.get(PREBID_EXT).get(BIDDER_EXT);

        bidders.forEach(extPrebidBidder::remove);

        return modifiedExt;
    }

    private static void logBidderOrImpExclusion(AuctionContext auctionContext, Imp imp,
                                                Set<String> dealsOnlyBiddersToRemove, String messageTemplate) {
        auctionContext.getDeepDebugLog().add(null, Category.cleanup, () ->
                String.format(messageTemplate, String.join(", ", dealsOnlyBiddersToRemove), imp.getId()));
    }
}
