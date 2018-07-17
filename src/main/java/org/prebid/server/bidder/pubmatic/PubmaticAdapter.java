package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.AdUnitBidWithParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.bidder.pubmatic.model.NormalizedPubmaticParams;
import org.prebid.server.bidder.pubmatic.proto.PubmaticParams;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pubmatic {@link Adapter} implementation.
 */
public class PubmaticAdapter extends OpenrtbAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PubmaticAdapter.class);

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    private final String endpointUrl;

    public PubmaticAdapter(Usersyncer usersyncer, String endpointUrl) {
        super(usersyncer);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final MultiMap headers = headers()
                .add(HttpHeaders.SET_COOKIE, makeUserCookie(preBidRequestContext));

        final BidRequest bidRequest = createBidRequest(adapterRequest, preBidRequestContext);
        final AdapterHttpRequest<BidRequest> httpRequest = AdapterHttpRequest.of(HttpMethod.POST, endpointUrl,
                bidRequest, headers);
        return Collections.singletonList(httpRequest);
    }

    private BidRequest createBidRequest(AdapterRequest adapterRequest, PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        final List<AdUnitBidWithParams<NormalizedPubmaticParams>> adUnitBidsWithParams =
                createAdUnitBidsWithParams(adUnitBids, preBidRequest.getTid());
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        final Publisher publisher = makePublisher(preBidRequestContext, adUnitBidsWithParams);

        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(imps)
                .app(makeApp(preBidRequestContext, publisher))
                .site(makeSite(preBidRequestContext, publisher))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .build();
    }

    private static List<AdUnitBidWithParams<NormalizedPubmaticParams>> createAdUnitBidsWithParams(
            List<AdUnitBid> adUnitBids, String requestId) {

        final List<AdUnitBidWithParams<NormalizedPubmaticParams>> adUnitBidWithParams = adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid, requestId)))
                .collect(Collectors.toList());

        // at least one adUnitBid of banner type must be with valid params
        if (adUnitBidWithParams.stream().noneMatch(PubmaticAdapter::isValidParams)) {
            throw new PreBidException("Incorrect adSlot / Publisher param");
        }

        return adUnitBidWithParams;
    }

    private static boolean isValidParams(AdUnitBidWithParams<NormalizedPubmaticParams> adUnitBidWithParams) {
        final NormalizedPubmaticParams params = adUnitBidWithParams.getParams();
        if (params == null) {
            return false;
        }

        // if adUnitBid has banner type, params should contains tagId, width and height fields
        return !adUnitBidWithParams.getAdUnitBid().getMediaTypes().contains(MediaType.banner)
                || ObjectUtils.allNotNull(params.getTagId(), params.getWidth(), params.getHeight());
    }

    private static NormalizedPubmaticParams parseAndValidateParams(AdUnitBid adUnitBid, String requestId) {
        final ObjectNode params = adUnitBid.getParams();
        if (params == null) {
            logWrongParams(requestId, null, adUnitBid, "Ignored bid: invalid JSON  [%s] err [%s]", null,
                    "params section is missing");
            return null;
        }

        final PubmaticParams pubmaticParams;
        try {
            pubmaticParams = Json.mapper.convertValue(params, PubmaticParams.class);
        } catch (IllegalArgumentException e) {
            logWrongParams(requestId, null, adUnitBid, "Ignored bid: invalid JSON  [%s] err [%s]", params, e);
            return null;
        }

        final String publisherId = pubmaticParams.getPublisherId();
        if (StringUtils.isEmpty(publisherId)) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: Publisher Id missing");
            return null;
        }

        final String adSlot = StringUtils.trimToNull(pubmaticParams.getAdSlot());
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

        return NormalizedPubmaticParams.of(publisherId, adSlot, adSlots[0], width, height);
    }

    private static void logWrongParams(String requestId, String publisherId, AdUnitBid adUnitBid, String errorMessage,
                                       Object... args) {
        logger.warn("[PUBMATIC] ReqID [{0}] PubID [{1}] AdUnit [{2}] BidID [{3}] {4}", requestId, publisherId,
                adUnitBid.getAdUnitCode(), adUnitBid.getBidId(), String.format(errorMessage, args));
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams<NormalizedPubmaticParams>> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .flatMap(adUnitBidWithParams -> makeImpsForAdUnitBid(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static Stream<Imp> makeImpsForAdUnitBid(AdUnitBidWithParams<NormalizedPubmaticParams> adUnitBidWithParams,
                                                    PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final NormalizedPubmaticParams params = adUnitBidWithParams.getParams();

        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid, params)
                        .id(adUnitBid.getAdUnitCode())
                        .instl(adUnitBid.getInstl())
                        .secure(preBidRequestContext.getSecure())
                        .tagid(mediaType == MediaType.banner && params != null ? params.getTagId() : null)
                        .build());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid,
                                                      NormalizedPubmaticParams params) {
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

    private static Banner makeBanner(AdUnitBid adUnitBid, NormalizedPubmaticParams params) {
        final List<Format> sizes = adUnitBid.getSizes();
        final Format format = sizes.get(0);
        return Banner.builder()
                .w(params != null ? params.getWidth() : format.getW())
                .h(params != null ? params.getHeight() : format.getH())
                .format(params != null ? null : sizes) // pubmatic doesn't support
                .topframe(adUnitBid.getTopframe())
                .build();
    }

    private static Publisher makePublisher(PreBidRequestContext preBidRequestContext,
                                           List<AdUnitBidWithParams<NormalizedPubmaticParams>> adUnitBidsWithParams) {
        return adUnitBidsWithParams.stream()
                .map(AdUnitBidWithParams::getParams)
                .filter(params -> params != null && params.getPublisherId() != null && params.getAdSlot() != null)
                .map(NormalizedPubmaticParams::getPublisherId)
                .reduce((first, second) -> second)
                .map(publisherId -> Publisher.builder()
                        .id(publisherId)
                        .domain(preBidRequestContext.getDomain())
                        .build())
                .orElse(null);
    }

    private static App makeApp(PreBidRequestContext preBidRequestContext, Publisher publisher) {
        final App app = preBidRequestContext.getPreBidRequest().getApp();
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
        final String cookieValue = preBidRequestContext.getUidsCookie().uidFrom(usersyncer.cookieFamilyName());
        return Cookie.cookie("KADUSERCOOKIE", ObjectUtils.firstNonNull(cookieValue, "")).encode();
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<BidRequest, BidResponse> exchangeCall) {
        return responseBidStream(exchangeCall.getResponse())
                .map(bid -> toBidBuilder(bid, adapterRequest))
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdapterRequest adapterRequest) {
        final AdUnitBid adUnitBid = lookupBid(adapterRequest.getAdUnitBids(), bid.getImpid());
        return Bid.builder()
                .bidder(adUnitBid.getBidderCode())
                .bidId(adUnitBid.getBidId())
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid());
    }

}
