package org.rtb.vexing.adapter.pubmatic;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.OpenrtbAdapter;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.adapter.pubmatic.model.PubmaticParams;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PubmaticAdapter extends OpenrtbAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PubmaticAdapter.class);

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    private final String endpointUrl;
    private final UsersyncInfo usersyncInfo;

    public PubmaticAdapter(String endpointUrl, String usersyncUrl, String externalUrl) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = encodeUrl("%s/setuid?bidder=pubmatic&uid=", externalUrl);

        return UsersyncInfo.builder()
                .url(String.format("%s%s", usersyncUrl, redirectUri))
                .type("iframe")
                .supportCORS(false)
                .build();
    }

    @Override
    public String code() {
        return "pubmatic";
    }

    @Override
    public String cookieFamily() {
        return "pubmatic";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }

    @Override
    public List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        final MultiMap headers = headers()
                .add(HttpHeaders.SET_COOKIE, makeUserCookie(preBidRequestContext));

        final BidRequest bidRequest = createBidRequest(bidder, preBidRequestContext);
        final HttpRequest httpRequest = HttpRequest.of(endpointUrl, headers, bidRequest);
        return Collections.singletonList(httpRequest);
    }

    private BidRequest createBidRequest(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        validateAdUnitBidsMediaTypes(bidder.adUnitBids);

        final List<AdUnitBidWithParams> adUnitBidsWithParams = createAdUnitBidsWithParams(bidder.adUnitBids,
                preBidRequestContext.preBidRequest.tid);
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        final Publisher publisher = makePublisher(preBidRequestContext, adUnitBidsWithParams);

        return BidRequest.builder()
                .id(preBidRequestContext.preBidRequest.tid)
                .at(1)
                .tmax(preBidRequestContext.preBidRequest.timeoutMillis)
                .imp(imps)
                .app(makeApp(preBidRequestContext, publisher))
                .site(makeSite(preBidRequestContext, publisher))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .build();
    }

    private static List<AdUnitBidWithParams> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids, String requestId) {
        final List<AdUnitBidWithParams> adUnitBidWithParams = adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid, requestId)))
                .collect(Collectors.toList());

        // at least one adUnitBid of banner type must be with valid params
        if (adUnitBidWithParams.stream().noneMatch(PubmaticAdapter::isValidParams)) {
            throw new PreBidException("Incorrect adSlot / Publisher param");
        }

        return adUnitBidWithParams;
    }

    private static boolean isValidParams(AdUnitBidWithParams adUnitBidWithParams) {
        final Params params = adUnitBidWithParams.params;
        if (params == null) {
            return false;
        }

        // if adUnitBid has banner type, params should contains tagId, width and height fields
        return !adUnitBidWithParams.adUnitBid.mediaTypes.contains(MediaType.banner)
                || ObjectUtils.allNotNull(params.tagId, params.width, params.height);
    }

    private static Params parseAndValidateParams(AdUnitBid adUnitBid, String requestId) {
        if (adUnitBid.params == null) {
            logWrongParams(requestId, null, adUnitBid, "Ignored bid: invalid JSON  [%s] err [%s]", null,
                    "params section is missing");
            return null;
        }

        final PubmaticParams pubmaticParams;
        try {
            pubmaticParams = DEFAULT_NAMING_MAPPER.convertValue(adUnitBid.params, PubmaticParams.class);
        } catch (IllegalArgumentException e) {
            logWrongParams(requestId, null, adUnitBid, "Ignored bid: invalid JSON  [%s] err [%s]", adUnitBid.params, e);
            return null;
        }

        final String publisherId = pubmaticParams.publisherId;
        if (StringUtils.isEmpty(publisherId)) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: Publisher Id missing");
            return null;
        }

        final String adSlot = StringUtils.trimToNull(pubmaticParams.adSlot);
        if (StringUtils.isEmpty(adSlot)) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: adSlot missing");
            return null;
        }

        final String[] adSlots = adSlot.split("@");
        if (adSlots.length != 2 || StringUtils.isEmpty(adSlots[0]) || StringUtils.isEmpty(adSlots[1])) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot [%s]", adSlot);
            return null;
        }

        final String[] adSizes = adSlots[1].toLowerCase().split("x");
        if (adSizes.length != 2) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSize [%s]", adSlots[1]);
            return null;
        }

        final int width;
        try {
            width = Integer.parseInt(adSizes[0].trim());
        } catch (NumberFormatException e) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot width [%s]", adSizes[0]);
            return null;
        }

        final int height;
        final String[] adSizeHeights = adSizes[1].split(":");
        try {
            height = Integer.parseInt(adSizeHeights[0].trim());
        } catch (NumberFormatException e) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot height [%s]", adSizes[0]);
            return null;
        }

        return Params.of(publisherId, adSlot, adSlots[0], width, height);
    }

    private static void logWrongParams(String requestId, String publisherId, AdUnitBid adUnitBid, String errorMessage,
                                       Object... args) {
        logger.warn("[PUBMATIC] ReqID [{0}] PubID [{1}] AdUnit [{2}] BidID [{3}] {4}", requestId, publisherId,
                adUnitBid.adUnitCode, adUnitBid.bidId, String.format(errorMessage, args));
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .flatMap(adUnitBidWithParams -> makeImpsForAdUnitBid(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static Stream<Imp> makeImpsForAdUnitBid(AdUnitBidWithParams adUnitBidWithParams,
                                                    PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.adUnitBid;
        final Params params = adUnitBidWithParams.params;

        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid, params)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(mediaType == MediaType.banner && params != null ? params.tagId : null)
                        .build());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid, Params params) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(videoBuilder(adUnitBid).build());
                break;
            case banner:
                impBuilder.banner(makeBanner(adUnitBid, params));
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
    }

    private static Banner makeBanner(AdUnitBid adUnitBid, Params params) {
        return Banner.builder()
                .w(params != null ? params.width : adUnitBid.sizes.get(0).getW())
                .h(params != null ? params.height : adUnitBid.sizes.get(0).getH())
                .format(params != null ? null : adUnitBid.sizes) // pubmatic doesn't support
                .topframe(adUnitBid.topframe)
                .build();
    }

    private static Publisher makePublisher(PreBidRequestContext preBidRequestContext,
                                           List<AdUnitBidWithParams> adUnitBidsWithParams) {
        return adUnitBidsWithParams.stream()
                .filter(adUnitBidWithParams -> adUnitBidWithParams.params != null
                        && adUnitBidWithParams.params.publisherId != null
                        && adUnitBidWithParams.params.adSlot != null)
                .map(adUnitBidWithParams -> adUnitBidWithParams.params.publisherId)
                .reduce((first, second) -> second)
                .map(publisherId -> Publisher.builder().id(publisherId).domain(preBidRequestContext.domain).build())
                .orElse(null);
    }

    private static App makeApp(PreBidRequestContext preBidRequestContext, Publisher publisher) {
        final App app = preBidRequestContext.preBidRequest.app;
        return app == null ? null : app.toBuilder()
                .publisher(publisher)
                .build();
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext, Publisher publisher) {
        final Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        return siteBuilder == null ? null : siteBuilder
                .publisher(publisher)
                .build();
    }

    private String makeUserCookie(PreBidRequestContext preBidRequestContext) {
        final String cookieValue = preBidRequestContext.uidsCookie.uidFrom(cookieFamily());
        return Cookie.cookie("KADUSERCOOKIE", ObjectUtils.firstNonNull(cookieValue, "")).encode();
    }

    @Override
    public List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) {
        return responseBidStream(exchangeCall.bidResponse)
                .map(bid -> toBidBuilder(bid, bidder))
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, Bidder bidder) {
        final AdUnitBid adUnitBid = lookupBid(bidder.adUnitBids, bid.getImpid());
        return Bid.builder()
                .bidder(adUnitBid.bidderCode)
                .bidId(adUnitBid.bidId)
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid());
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class Params {

        String publisherId;

        String adSlot;

        String tagId;

        Integer width;

        Integer height;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class AdUnitBidWithParams {

        AdUnitBid adUnitBid;

        Params params;
    }
}
