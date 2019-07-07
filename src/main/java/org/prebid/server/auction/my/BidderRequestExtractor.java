package org.prebid.server.auction.my;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.response.BidderInfo;

public class BidderRequestExtractor {
	private static final DecimalFormat ROUND_TWO_DECIMALS =
		new DecimalFormat("###.##", DecimalFormatSymbols.getInstance(Locale.US));


	/**
	 * Takes an OpenRTB request and returns the OpenRTB requests sanitized for each bidder.
	 * <p>
	 * This will copy the {@link BidRequest} into a list of requests, where the bidRequest.imp[].ext field
	 * will only consist of the "prebid" field and the field for the appropriate bidder parameters. We will drop all
	 * extended fields beyond this context, so this will not be compatible with any other uses of the extension area
	 * i.e. the bidders will not see any other extension fields. If Imp extension name is alias, which is also defined
	 * in bidRequest.ext.prebid.aliases and valid, separate {@link BidRequest} will be created for this alias and sent
	 * to appropriate bidder.
	 * For example suppose {@link BidRequest} has two {@link Imp}s. First one with imp.ext[].rubicon and
	 * imp.ext[].rubiconAlias and second with imp.ext[].appnexus and imp.ext[].rubicon. Three {@link BidRequest}s will
	 * be created:
	 * 1. {@link BidRequest} with one {@link Imp}, where bidder extension points to rubiconAlias extension and will be
	 * sent to Rubicon bidder.
	 * 2. {@link BidRequest} with two {@link Imp}s, where bidder extension points to appropriate rubicon extension from
	 * original {@link BidRequest} and will be sent to Rubicon bidder.
	 * 3. {@link BidRequest} with one {@link Imp}, where bidder extension points to appnexus extension and will be sent
	 * to Appnexus bidder.
	 * <p>
	 * Each of the created {@link BidRequest}s will have bidrequest.user.buyerid field populated with the value from
	 * bidrequest.user.ext.prebid.buyerids or {@link UidsCookie} corresponding to bidder's family name unless buyerid
	 * is already in the original OpenRTB request (in this case it will not be overridden).
	 * In case if bidrequest.user.ext.prebid.buyerids contains values after extracting those values it will be cleared
	 * in order to avoid leaking of buyerids across bidders.
	 * <p>
	 * NOTE: the return list will only contain entries for bidders that both have the extension field in at least one
	 * {@link Imp}, and are known to {@link BidderCatalog} or aliases from bidRequest.ext.prebid.aliases.
	 */
	private Future<List<BidderRequest>> extractBidderRequests(BidRequest bidRequest, ExtBidRequest requestExt,
		UidsCookie uidsCookie, Map<String, String> aliases,
		String publisherId, Timeout timeout) {
		// sanity check: discard imps without extension
		final List<Imp> imps = bidRequest.getImp().stream()
			.filter(imp -> imp.getExt() != null)
			.collect(Collectors.toList());

		// identify valid bidders and aliases out of imps
		final List<String> bidders = imps.stream()
			.flatMap(imp -> asStream(imp.getExt().fieldNames())
				.filter(bidder -> !Objects.equals(bidder, PREBID_EXT) && !Objects.equals(bidder, CONTEXT_EXT))
				.filter(bidder -> isValidBidder(bidder, aliases)))
			.distinct()
			.collect(Collectors.toList());

		final ExtUser extUser = extUser(bidRequest.getUser());
		final ExtRegs extRegs = extRegs(bidRequest.getRegs());

		return getVendorsToGdprPermission(bidRequest, bidders, aliases, publisherId, extUser, extRegs, timeout)
			.map(vendorsToGdpr -> makeBidderRequests(bidders, aliases, bidRequest, requestExt, uidsCookie,
				extUser, extRegs, imps, vendorsToGdpr));
	}


	/**
	 * Checks if bidder name is valid in case when bidder can also be alias name.
	 */
	private boolean isValidBidder(String bidder, Map<String, String> aliases) {
		return bidderCatalog.isValidName(bidder) || aliases.containsKey(bidder);
	}

	/**
	 * Returns {@link Future&lt;{@link Map}&lt;{@link Integer}, {@link Boolean}&gt;&gt;}, where bidders vendor id mapped
	 * to enabling or disabling GDPR in scope of pbs server. If bidder vendor id is not present in map, it means that
	 * pbs not enforced particular bidder to follow pbs GDPR procedure.
	 */
	private Future<Map<Integer, Boolean>> getVendorsToGdprPermission(BidRequest bidRequest, List<String> bidders,
		Map<String, String> aliases,
		String publisherId, ExtUser extUser,
		ExtRegs extRegs, Timeout timeout) {
		final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;
		final String gdprAsString = gdpr != null ? gdpr.toString() : null;
		final String gdprConsent = extUser != null ? extUser.getConsent() : null;
		final Device device = bidRequest.getDevice();
		final String ipAddress = useGeoLocation && device != null ? device.getIp() : null;
		final Set<Integer> vendorIds = extractGdprEnforcedVendors(bidders, aliases);

		return gdprService.isGdprEnforced(gdprAsString, publisherId, vendorIds, timeout)
			.compose(gdprEnforced -> !gdprEnforced
				? Future.succeededFuture(Collections.emptyMap())
				: gdprService.resultByVendor(vendorIds, gdprAsString, gdprConsent, ipAddress, timeout)
					.map(GdprResponse::getVendorsToGdpr));
	}


	/**
	 * Extracts {@link ExtRegs} from {@link Regs}.
	 */
	private static ExtRegs extRegs(Regs regs) {
		final ObjectNode regsExt = regs != null ? regs.getExt() : null;
		if (regsExt != null) {
			try {
				return Json.mapper.treeToValue(regsExt, ExtRegs.class);
			} catch (JsonProcessingException e) {
				throw new PreBidException(String.format("Error decoding bidRequest.regs.ext: %s", e.getMessage()), e);
			}
		}
		return null;
	}

	/**
	 * Extracts GDPR enforced vendor IDs.
	 */
	private Set<Integer> extractGdprEnforcedVendors(List<String> bidders, Map<String, String> aliases) {
		return bidders.stream()
			.map(bidder -> bidderCatalog.bidderInfoByName(resolveBidder(bidder, aliases)).getGdpr())
			.filter(BidderInfo.GdprInfo::isEnforced)
			.map(BidderInfo.GdprInfo::getVendorId)
			.collect(Collectors.toSet());
	}

	/**
	 * Extracts {@link ExtUser} from request.user.ext or returns null if not presents.
	 */
	private static ExtUser extUser(User user) {
		final ObjectNode userExt = user != null ? user.getExt() : null;
		if (userExt != null) {
			try {
				return Json.mapper.treeToValue(userExt, ExtUser.class);
			} catch (JsonProcessingException e) {
				throw new PreBidException(String.format("Error decoding bidRequest.user.ext: %s", e.getMessage()), e);
			}
		}
		return null;
	}



	/**
	 * Splits the input request into requests which are sanitized for each bidder. Intended behavior is:
	 * <p>
	 * - bidrequest.imp[].ext will only contain the "prebid" field and a "bidder" field which has the params for
	 * the intended Bidder.
	 * <p>
	 * - bidrequest.user.buyeruid will be set to that Bidder's ID.
	 * <p>
	 * - bidrequest.ext.prebid.data.bidders will be removed.
	 * <p>
	 * - bidrequest.user.ext.data, bidrequest.app.ext.data and bidrequest.site.ext.data will be removed for bidders
	 * that don't have first party data allowed.
	 */
	private List<BidderRequest> makeBidderRequests(List<String> bidders, Map<String, String> aliases,
		BidRequest bidRequest, ExtBidRequest requestExt,
		UidsCookie uidsCookie, ExtUser extUser, ExtRegs extRegs,
		List<Imp> imps, Map<Integer, Boolean> vendorsToGdpr) {

		final Map<String, String> uidsBody = uidsFromBody(extUser);

		final Regs regs = bidRequest.getRegs();
		final boolean coppaMasking = isCoppaMaskingRequired(regs);

		final Device device = bidRequest.getDevice();
		final Integer deviceLmt = device != null ? device.getLmt() : null;
		final Map<String, Boolean> bidderToGdprMasking = bidders.stream()
			.collect(Collectors.toMap(Function.identity(),
				bidder -> isGdprMaskingRequiredFor(bidder, aliases, vendorsToGdpr, deviceLmt)));

		final List<String> firstPartyDataBidders = firstPartyDataBidders(requestExt);
		final App app = bidRequest.getApp();
		final ExtApp extApp = extApp(app);
		final Site site = bidRequest.getSite();
		final ExtSite extSite = extSite(site);

		final Map<String, User> bidderToUser = bidders.stream()
			.collect(Collectors.toMap(Function.identity(),
				bidder -> prepareUser2(bidRequest.getUser(), extUser, bidder, aliases, uidsBody, uidsCookie,
					firstPartyDataBidders.contains(bidder))));

		privacyEnforcementService.mask(bidderToUser, device, regs, extRegs).entries().strream() // map of bidder to UserDeviceRegs
			.map(entry -> BidderRequest.of(...))


		final List<BidderRequest> bidderRequests = bidders.stream()
			// for each bidder create a new request that is a copy of original request except buyerid, imp
			// extensions and ext.prebid.data.bidders.
			// Also, check whether to pass user.ext.data, app.ext.data and site.ext.data or not.
			.map(bidder -> BidderRequest.of(bidder, bidRequest.toBuilder()
				.user(prepareUser(bidRequest.getUser(), extUser, bidder, aliases, uidsBody, uidsCookie,
					firstPartyDataBidders.contains(bidder), coppaMasking, bidderToGdprMasking.get(bidder)))
				.device(prepareDevice(device, coppaMasking, bidderToGdprMasking.get(bidder)))
				.regs(prepareRegs(regs, extRegs, bidderToGdprMasking.get(bidder)))
				.imp(prepareImps(bidder, imps, firstPartyDataBidders.contains(bidder)))
				.app(prepareApp(app, extApp, firstPartyDataBidders.contains(bidder)))
				.site(prepareSite(site, extSite, firstPartyDataBidders.contains(bidder)))
				.ext(cleanExtPrebidDataBidders(bidder, firstPartyDataBidders, requestExt, bidRequest.getExt()))
				.build()))
			.collect(Collectors.toList());

		// randomize the list to make the auction more fair
		Collections.shuffle(bidderRequests);

		return bidderRequests;
	}
	/**
	 * Returns UIDs from request.user.ext or empty map if not defined.
	 */
	private static Map<String, String> uidsFromBody(ExtUser extUser) {
		return extUser != null && extUser.getPrebid() != null
			// as long as ext.prebid exists we are guaranteed that user.ext.prebid.buyeruids also exists
			? extUser.getPrebid().getBuyeruids()
			: Collections.emptyMap();
	}

	/**
	 * Determines if COPPA is required.
	 */
	private static boolean isCoppaMaskingRequired(Regs regs) {
		return regs != null && Objects.equals(regs.getCoppa(), 1);
	}

	/**
	 * Returns the name associated with bidder if bidder is an alias.
	 * If it's not an alias, the bidder is returned.
	 */
	private static String resolveBidder(String bidder, Map<String, String> aliases) {
		return aliases.getOrDefault(bidder, bidder);
	}

	/**
	 * Returns flag if GDPR masking is required for bidder.
	 */
	private boolean isGdprMaskingRequiredFor(String bidder, Map<String, String> aliases,
		Map<Integer, Boolean> vendorToGdprPermission, Integer deviceLmt) {
		final boolean maskingRequired;
		final boolean isLmtEnabled = deviceLmt != null && deviceLmt.equals(1);
		if (vendorToGdprPermission.isEmpty() && !isLmtEnabled) {
			maskingRequired = false;
		} else {
			final String resolvedBidderName = resolveBidder(bidder, aliases);
			final Boolean gdprAllowsUserData = vendorToGdprPermission.get(
				bidderCatalog.bidderInfoByName(resolvedBidderName).getGdpr().getVendorId());

			// if bidder was not found in vendorToGdprPermission, it means that it was not enforced for GDPR,
			// so request for this bidder should be sent without changes
			maskingRequired = (gdprAllowsUserData != null && !gdprAllowsUserData) || isLmtEnabled;

			if (maskingRequired) {
				metrics.updateGdprMaskedMetric(resolvedBidderName);
			}
		}
		return maskingRequired;
	}

	/**
	 * Extracts a list of bidders for which first party data is allowed from {@link ExtRequestPrebidData} model.
	 */
	private static List<String> firstPartyDataBidders(ExtBidRequest requestExt) {
		final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
		final ExtRequestPrebidData data = prebid == null ? null : prebid.getData();
		final List<String> bidders = data == null ? null : data.getBidders();
		return ObjectUtils.defaultIfNull(bidders, Collections.emptyList());
	}

	/**
	 * Extracts {@link ExtApp} from {@link App}.
	 */
	private static ExtApp extApp(App app) {
		final ObjectNode appExt = app == null ? null : app.getExt();
		if (appExt != null) {
			try {
				return Json.mapper.treeToValue(appExt, ExtApp.class);
			} catch (JsonProcessingException e) {
				throw new PreBidException(String.format("Error decoding bidRequest.app.ext: %s", e.getMessage()), e);
			}
		}
		return null;
	}

	/**
	 * Extracts {@link ExtSite} from {@link Site}.
	 */
	private static ExtSite extSite(Site site) {
		final ObjectNode siteExt = site == null ? null : site.getExt();
		if (siteExt != null) {
			try {
				return Json.mapper.treeToValue(siteExt, ExtSite.class);
			} catch (JsonProcessingException e) {
				throw new PreBidException(String.format("Error decoding bidRequest.site.ext: %s", e.getMessage()), e);
			}
		}
		return null;
	}

	/**
	 * Checks whether to pass the app.ext.data depending on request having a first party data
	 * allowed for given bidder or not.
	 */
	private static App prepareApp(App app, ExtApp extApp, boolean useFirstPartyData) {
		final ObjectNode extSiteDataNode = extApp == null ? null : extApp.getData();

		return app != null && extSiteDataNode != null && !useFirstPartyData
			? app.toBuilder().ext(Json.mapper.valueToTree(ExtApp.of(extApp.getPrebid(), null))).build()
			: app;
	}

	/**
	 * Checks whether to pass the site.ext.data depending on request having a first party data
	 * allowed for given bidder or not.
	 */
	private static Site prepareSite(Site site, ExtSite extSite, boolean useFirstPartyData) {
		final ObjectNode extSiteDataNode = extSite == null ? null : extSite.getData();

		return site != null && extSiteDataNode != null && !useFirstPartyData
			? site.toBuilder().ext(Json.mapper.valueToTree(ExtSite.of(extSite.getAmp(), null))).build()
			: site;
	}

	/**
	 * Removes all bidders except the given bidder from bidrequest.ext.prebid.data.bidders
	 * to hide list of allowed bidders from initial request.
	 */
	private static ObjectNode cleanExtPrebidDataBidders(String bidder, List<String> firstPartyDataBidders,
		ExtBidRequest requestExt, ObjectNode requestExtNode) {
		if (firstPartyDataBidders.isEmpty()) {
			return requestExtNode;
		}

		final ExtRequestPrebidData prebidData = firstPartyDataBidders.contains(bidder)
			? ExtRequestPrebidData.of(Collections.singletonList(bidder))
			: null;
		return Json.mapper.valueToTree(ExtBidRequest.of(requestExt.getPrebid().toBuilder()
			.data(prebidData)
			.build()));
	}
	/**
	 * Returns original {@link User} if user.buyeruid already contains uid value for bidder.
	 * Otherwise, returns new {@link User} containing updated {@link ExtUser} and user.buyeruid.
	 * <p>
	 * Also, applies COPPA, GDPR and First Data Party processing.
	 */
	private User prepareUser(User user, ExtUser extUser, String bidder, Map<String, String> aliases,
		Map<String, String> uidsBody, UidsCookie uidsCookie,
		boolean useFirstPartyData) {

		final ObjectNode updatedExt = updateUserExt(extUser, useFirstPartyData);

		//TODO ASK We do unnecessary job
		final String updatedBuyerUid = !coppaMaskingRequired && !gdprMaskingRequired
			? updateUserBuyerUid(user, bidder, aliases, uidsBody, uidsCookie)
			: null;

		if (updatedExt != null || updatedBuyerUid != null || coppaMaskingRequired || gdprMaskingRequired) {
			final User.UserBuilder builder;
			if (user != null) {
				builder = user.toBuilder();

				if (updatedExt != null) {
					builder.ext(updatedExt);
				}

				// clean user.id, user.yob, and user.gender (COPPA masking)
				if (coppaMaskingRequired) {
					builder
						.id(null)
						.yob(null)
						.gender(null);
				}

				// clean user.buyeruid and user.geo (COPPA and GDPR masking)
				if (coppaMaskingRequired || gdprMaskingRequired) {
					builder
						.buyeruid(null)
						.geo(coppaMaskingRequired ? maskGeoForCoppa(user.getGeo()) : maskGeoForGdpr(user.getGeo()));
				}
			} else {
				builder = User.builder();
			}

			if (updatedBuyerUid != null) {
				builder.buyeruid(updatedBuyerUid);
			}

			return builder.build();
		}

		return user;
	}


	/**
	 * Returns json encoded {@link ObjectNode} of {@link ExtUser} with changes applied:
	 * <p>
	 * - Removes request.user.ext.prebid.buyeruids to avoid leaking of buyeruids across bidders.
	 * <p>
	 * - Removes request.user.ext.data if bidder doesn't allow first party data to be passed.
	 * <p>
	 * Returns null if {@link ExtUser} doesn't need to be updated.
	 */
	private static ObjectNode updateUserExt(ExtUser extUser, boolean useFirstPartyData) {
		if (extUser != null) {
			final boolean removePrebid = extUser.getPrebid() != null;
			final boolean removeFirstPartyData = !useFirstPartyData && extUser.getData() != null;

			if (removePrebid || removeFirstPartyData) {
				final ExtUser.ExtUserBuilder builder = extUser.toBuilder();

				if (removePrebid) {
					builder.prebid(null);
				}
				if (removeFirstPartyData) {
					builder.data(null);
				}

				return Json.mapper.valueToTree(builder.build());
			}
		}
		return null;
	}

	/**
	 * Returns updated buyerUid or null if it doesn't need to be updated.
	 */
	private String updateUserBuyerUid(User user, String bidder, Map<String, String> aliases,
		Map<String, String> uidsBody, UidsCookie uidsCookie) {
		final String buyerUidFromBodyOrCookie = extractUid(uidsBody, uidsCookie, resolveBidder(bidder, aliases));
		final String buyerUidFromUser = user != null ? user.getBuyeruid() : null;

		return StringUtils.isBlank(buyerUidFromUser) && StringUtils.isNotBlank(buyerUidFromBodyOrCookie)
			? buyerUidFromBodyOrCookie
			: null;
	}

	/**
	 * Returns masked for COPPA {@link Geo}.
	 */
	private static Geo maskGeoForCoppa(Geo geo) {
		final Geo updatedGeo = geo != null
			? geo.toBuilder().lat(null).lon(null).metro(null).city(null).zip(null).build()
			: null;
		return updatedGeo == null || updatedGeo.equals(Geo.EMPTY) ? null : updatedGeo;
	}

	/**
	 * Returns masked for GDPR {@link Geo} by rounding lon and lat properties.
	 */
	private static Geo maskGeoForGdpr(Geo geo) {
		return geo != null
			? geo.toBuilder()
			.lat(maskGeoCoordinate(geo.getLat()))
			.lon(maskGeoCoordinate(geo.getLon()))
			.build()
			: null;
	}

	/**
	 * Returns masked geo coordinate with rounded value to two decimals.
	 */
	private static Float maskGeoCoordinate(Float coordinate) {
		return coordinate != null ? Float.valueOf(ROUND_TWO_DECIMALS.format(coordinate)) : null;
	}

	/**
	 * Prepares device, suppresses device information if COPPA or GDPR masking is required.
	 */
	private static Device prepareDevice(Device device, boolean coppaMaskingRequired, boolean gdprMaskingRequired) {
		return device != null && (coppaMaskingRequired || gdprMaskingRequired)
			? device.toBuilder()
			.ip(maskIpv4(device.getIp()))
			.ipv6(maskIpv6(device.getIpv6()))
			.geo(coppaMaskingRequired ? maskGeoForCoppa(device.getGeo()) : maskGeoForGdpr(device.getGeo()))
			.ifa(null)
			.macsha1(null).macmd5(null)
			.dpidsha1(null).dpidmd5(null)
			.didsha1(null).didmd5(null)
			.build()
			: device;
	}

	/**
	 * Masks ip v4 address by replacing last group with zero.
	 */
	private static String maskIpv4(String ip) {
		return maskIp(ip, '.');
	}

	/**
	 * Masks ip v6 address by replacing last group with zero.
	 */
	private static String maskIpv6(String ip) {
		return maskIp(ip, ':');
	}

	/**
	 * Masks ip address by replacing bits after last separator with zero.
	 */
	private static String maskIp(String ip, char delimiter) {
		return StringUtils.isNotEmpty(ip) ? ip.substring(0, ip.lastIndexOf(delimiter) + 1) + "0" : ip;
	}

	/**
	 * Sets GDPR value 1, if bidder required GDPR masking, but regs.ext.gdpr is not defined.
	 */
	private static Regs prepareRegs(Regs regs, ExtRegs extRegs, boolean gdprMaskingRequired) {
		final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;

		return gdpr == null && gdprMaskingRequired
			? Regs.of(regs != null ? regs.getCoppa() : null, Json.mapper.valueToTree(ExtRegs.of(1)))
			: regs;
	}

	/**
	 * For each given imp creates a new imp with extension crafted to contain only "prebid", "context" and
	 * bidder-specific extension.
	 */
	private static List<Imp> prepareImps(String bidder, List<Imp> imps, boolean useFirstPartyData) {
		return imps.stream()
			.filter(imp -> imp.getExt().hasNonNull(bidder))
			.map(imp -> imp.toBuilder()
				.ext(prepareImpExt(bidder, imp.getExt(), useFirstPartyData))
				.build())
			.collect(Collectors.toList());
	}

	/**
	 * Creates a new imp extension for particular bidder having:
	 * <ul>
	 * <li>"prebid" field populated with an imp.ext.prebid field value, may be null</li>
	 * <li>"context" field populated with an imp.ext.context field value, may be null</li>
	 * <li>"bidder" field populated with an imp.ext.{bidder} field value, not null</li>
	 * </ul>
	 */
	private static ObjectNode prepareImpExt(String bidder, ObjectNode impExt, boolean useFirstPartyData) {
		final ObjectNode result = Json.mapper.valueToTree(
			ExtPrebid.of(impExt.get(PREBID_EXT), impExt.get(bidder)));

		if (useFirstPartyData) {
			result.set(CONTEXT_EXT, impExt.get(CONTEXT_EXT));
		}

		return result;
	}

	/**
	 * Extracts UID from uids from body or {@link UidsCookie}. If absent returns null.
	 */
	private String extractUid(Map<String, String> uidsBody, UidsCookie uidsCookie, String bidder) {
		final String uid = uidsBody.get(bidder);
		return StringUtils.isNotBlank(uid)
			? uid
			: uidsCookie.uidFrom(bidderCatalog.usersyncerByName(bidder).getCookieFamilyName());
	}
}
