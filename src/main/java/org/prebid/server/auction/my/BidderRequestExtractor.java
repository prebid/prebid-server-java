package org.prebid.server.auction.my;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.UserDeviceRegs;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BidderRequestExtractor {
    private static final DecimalFormat ROUND_TWO_DECIMALS =
            new DecimalFormat("###.##", DecimalFormatSymbols.getInstance(Locale.US));

    private static final String PREBID_EXT = "prebid";
    private static final String CONTEXT_EXT = "context";

    private BidderCatalog bidderCatalog;
    private PrivacyEnforcement privacyEnforcement;

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


        return makeBidderRequests(bidders, aliases, publisherId, bidRequest, requestExt, uidsCookie, imps, timeout);
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Checks if bidder name is valid in case when bidder can also be alias name.
     */
    private boolean isValidBidder(String bidder, Map<String, String> aliases) {
        return bidderCatalog.isValidName(bidder) || aliases.containsKey(bidder);
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
    private Future<List<BidderRequest>> makeBidderRequests(List<String> bidders, Map<String, String> aliases,
                                                           String pulisherId,
                                                           BidRequest bidRequest, ExtBidRequest requestExt,
                                                           UidsCookie uidsCookie,
                                                           List<Imp> imps, Timeout timeout) {
        final ExtUser extUser = extUser(bidRequest.getUser());
        final ExtRegs extRegs = extRegs(bidRequest.getRegs());

        final Map<String, String> uidsBody = uidsFromBody(extUser);

        //TODO We use this only in privacy, I can remove, but in a future we mb need them
        final Regs regs = bidRequest.getRegs();
        final Device device = bidRequest.getDevice();

        //TODO also can pull out to privacy
        final List<String> firstPartyDataBidders = firstPartyDataBidders(requestExt);

        final Map<String, User> bidderToUser = bidders.stream()
                .collect(Collectors.toMap(Function.identity(),
                        bidder -> prepareUser2(bidRequest.getUser(), extUser, bidder, aliases, uidsBody, uidsCookie,
                                firstPartyDataBidders.contains(bidder))));

        return privacyEnforcement.mask(bidderToUser, device, regs, extRegs, bidRequest, bidders, aliases, pulisherId, extUser, timeout)
                // map of bidder to UserDeviceRegs
                .map(stringUserDeviceRegsMap -> {
                    final List<BidderRequest> bidderRequests = stringUserDeviceRegsMap.entrySet().stream()
                            .map(entry -> createBidderRequest(entry.getKey(), entry.getValue(), bidRequest, requestExt, imps, firstPartyDataBidders))
                            .collect(Collectors.toList());
                    Collections.shuffle(bidderRequests);
                    return bidderRequests;
                });
    }

    private BidderRequest createBidderRequest(String bidder, UserDeviceRegs userDeviceRegs, BidRequest bidRequest, ExtBidRequest requestExt, List<Imp> imps, List<String> firstPartyDataBidders) {
        final App app = bidRequest.getApp();
        final ExtApp extApp = extApp(app);
        final Site site = bidRequest.getSite();
        final ExtSite extSite = extSite(site);

        return BidderRequest.of(bidder, bidRequest.toBuilder()
                .user(userDeviceRegs.getUser())
                .device(userDeviceRegs.getDevice())
                .regs(userDeviceRegs.getRegs())
                .imp(prepareImps(bidder, imps, firstPartyDataBidders.contains(bidder)))
                .app(prepareApp(app, extApp, firstPartyDataBidders.contains(bidder)))
                .site(prepareSite(site, extSite, firstPartyDataBidders.contains(bidder)))
                .ext(cleanExtPrebidDataBidders(bidder, firstPartyDataBidders, requestExt, bidRequest.getExt()))
                .build());
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
     * Returns the name associated with bidder if bidder is an alias.
     * If it's not an alias, the bidder is returned.
     */
    private static String resolveBidder(String bidder, Map<String, String> aliases) {
        return aliases.getOrDefault(bidder, bidder);
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
     */
    private User prepareUser2(User user, ExtUser extUser, String bidder, Map<String, String> aliases,
                              Map<String, String> uidsBody, UidsCookie uidsCookie, boolean useFirstPartyData) {
        if (user == null) {
            return null;
        }

        final ObjectNode updatedExt = updateUserExt(extUser, useFirstPartyData);
        final String updatedBuyerUid = updateUserBuyerUid(user, bidder, aliases, uidsBody, uidsCookie);

        if (updatedBuyerUid != null || updatedExt != null) {
            final User.UserBuilder userBuilder = user.toBuilder();

            if (updatedExt != null) {
                userBuilder.ext(updatedExt);
            }

            //!coppaMaskingRequired && !gdprMaskingRequired
            if (updatedBuyerUid != null) {
                userBuilder.buyeruid(updatedBuyerUid);
            }
            return userBuilder.build();
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
