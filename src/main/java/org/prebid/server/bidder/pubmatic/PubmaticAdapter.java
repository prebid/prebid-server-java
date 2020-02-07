package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.type.TypeReference;
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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.model.AdUnitBidWithParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.bidder.pubmatic.model.NormalizedPubmaticParams;
import org.prebid.server.bidder.pubmatic.proto.PubmaticParams;
import org.prebid.server.bidder.pubmatic.proto.PubmaticRequestExt;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pubmatic {@link Adapter} implementation.
 */
public class PubmaticAdapter extends OpenrtbAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PubmaticAdapter.class);

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public PubmaticAdapter(String cookieFamilyName, String endpointUrl, JacksonMapper mapper) {
        super(cookieFamilyName);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final MultiMap headers = headers()
                .add(HttpUtil.SET_COOKIE_HEADER, makeUserCookie(preBidRequestContext));

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
                .ext(makeBidExt(adUnitBidsWithParams))
                .build();
    }

    // Parse Wrapper Extension i.e. ProfileID and VersionID only once per request
    private ObjectNode makeBidExt(List<AdUnitBidWithParams<NormalizedPubmaticParams>> adUnitBidsWithParams) {
        final ObjectNode wrapExt = adUnitBidsWithParams.stream()
                .map(AdUnitBidWithParams::getParams)
                .filter(Objects::nonNull)
                .map(NormalizedPubmaticParams::getWrapExt)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        if (wrapExt != null) {
            return mapper.mapper().valueToTree(PubmaticRequestExt.of(wrapExt));
        }
        return null;
    }

    private List<AdUnitBidWithParams<NormalizedPubmaticParams>> createAdUnitBidsWithParams(
            List<AdUnitBid> adUnitBids, String requestId) {
        final List<String> errors = new ArrayList<>();
        final List<AdUnitBidWithParams<NormalizedPubmaticParams>> adUnitBidWithParams = adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid,
                        parseAndValidateParams(adUnitBid, requestId, errors)))
                .collect(Collectors.toList());

        // at least one adUnitBid of banner type must be with valid params
        if (adUnitBidWithParams.stream().noneMatch(PubmaticAdapter::isValidParams)) {
            throw new PreBidException("Incorrect adSlot / Publisher param, "
                    + "Error list: [" + String.join(",", errors) + "]");
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

    private NormalizedPubmaticParams parseAndValidateParams(AdUnitBid adUnitBid,
                                                            String requestId, List<String> errors) {
        final ObjectNode params = adUnitBid.getParams();
        final List<Format> sizes = adUnitBid.getSizes();
        final String bidId = adUnitBid.getBidId();
        if (params == null) {
            errors.add(paramError(bidId, "Params section is missing", null));
            logWrongParams(requestId, null, adUnitBid, "Ignored bid: invalid JSON  [%s] err [%s]", null,
                    "params section is missing");
            return null;
        }

        final PubmaticParams pubmaticParams;
        try {
            pubmaticParams = mapper.mapper().convertValue(params, PubmaticParams.class);
        } catch (IllegalArgumentException e) {
            errors.add(paramError(bidId, "Invalid BidParam", params));
            logWrongParams(requestId, null, adUnitBid, "Ignored bid: invalid JSON  [%s] err [%s]", params, e);
            return null;
        }

        final String publisherId = pubmaticParams.getPublisherId();
        if (StringUtils.isEmpty(publisherId)) {
            errors.add(paramError(bidId, "Missing PubID", params));
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: Publisher Id missing");
            return null;
        }

        final String adSlot = StringUtils.trimToNull(pubmaticParams.getAdSlot());
        Integer width = null;
        Integer height = null;
        String tagId = null;
        boolean slotWithoutSize = true;
        if (!StringUtils.isEmpty(adSlot)) {
            if (!adSlot.contains("@")) {
                tagId = adSlot;
            } else {
                final String[] adSlots = adSlot.split("@");
                if (adSlots.length != 2 || StringUtils.isEmpty(adSlots[0].trim())
                        || StringUtils.isEmpty(adSlots[1].trim())) {
                    errors.add(paramError(bidId, "Invalid AdSlot", params));
                    logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot [%s]", adSlot);
                    return null;
                }
                tagId = adSlots[0].trim();
                final String[] adSizes = adSlots[1].toLowerCase().split("x");
                if (adSizes.length != 2) {
                    errors.add(paramError(bidId, "Invalid AdSize", params));
                    logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSize [%s]",
                            adSlots[1]);
                    return null;
                }

                try {
                    width = Integer.parseInt(adSizes[0].trim());
                } catch (NumberFormatException e) {
                    errors.add(paramError(bidId, "Invalid Width", params));
                    logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot width [%s]",
                            adSizes[0]);
                    return null;
                }

                final String[] adSizeHeights = adSizes[1].split(":");
                try {
                    height = Integer.parseInt(adSizeHeights[0].trim());
                } catch (NumberFormatException e) {
                    errors.add(paramError(bidId, "Invalid Height", params));
                    logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot height [%s]",
                            adSizes[0]);
                    return null;
                }
                slotWithoutSize = false;
            }
        }
        if (slotWithoutSize) {
            if (CollectionUtils.isEmpty(sizes)) {
                errors.add(paramError(bidId, "Invalid AdSize", params));
                logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: Size missing");
                return null;
            }
            height = sizes.get(0).getH();
            width = sizes.get(0).getW();
        }

        final ObjectNode wrapExt;
        if (pubmaticParams.getWrapper() != null) {
            try {
                mapper.mapper().convertValue(pubmaticParams.getWrapper(), new TypeReference<Map<String, Integer>>() {
                });
            } catch (IllegalArgumentException e) {
                errors.add(paramError(bidId, "Invalid WrapperExt", params));
                logWrongParams(requestId, pubmaticParams.getPublisherId(), adUnitBid,
                        "Ignored bid: Wrapper Extension Invalid");
                return null;
            }
            wrapExt = pubmaticParams.getWrapper();
        } else {
            wrapExt = null;
        }

        final ObjectNode keyValue;
        final List<String> keywords = makeKeywords(pubmaticParams.getKeywords());
        if (!keywords.isEmpty()) {
            try {
                keyValue = mapper.mapper().readValue("{" + String.join(",", keywords) + "}", ObjectNode.class);
            } catch (IOException e) {
                errors.add(String.format("Failed to create keywords with error: %s", e.getMessage()));
                return null;
            }
        } else {
            keyValue = null;
        }

        return NormalizedPubmaticParams.of(publisherId, adSlot, tagId, width, height, wrapExt, keyValue);
    }

    private static String paramError(String bidId, String message, ObjectNode params) {
        return String.format("BidID:%s;Error:%s;param:%s", bidId, message, params);
    }

    private static List<String> makeKeywords(Map<String, String> keywords) {
        if (keywords == null) {
            return Collections.emptyList();
        }
        final List<String> keywordPair = new ArrayList<>();
        for (Map.Entry<String, String> entry : keywords.entrySet()) {
            final String key = entry.getKey();
            if (StringUtils.isBlank(entry.getValue())) {
                logger.warn(String.format("No values present for key = %s", key));
            } else {
                keywordPair.add(String.format("\"%s\":\"%s\"", key, entry.getValue()));
            }
        }
        return keywordPair;
    }

    private static void logWrongParams(String requestId, String publisherId, AdUnitBid adUnitBid, String errorMessage,
                                       Object... args) {
        logger.warn("[PUBMATIC] ReqID [{0}] PubID [{1}] AdUnit [{2}] BidID [{3}] {4}", requestId, publisherId,
                adUnitBid.getAdUnitCode(), adUnitBid.getBidId(), String.format(errorMessage, args));
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams<NormalizedPubmaticParams>> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .filter(PubmaticAdapter::containsAnyAllowedMediaType)
                .map(adUnitBidWithParams -> makeImp(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static boolean containsAnyAllowedMediaType(
            AdUnitBidWithParams<NormalizedPubmaticParams> adUnitBidWithParams) {
        return CollectionUtils.containsAny(adUnitBidWithParams.getAdUnitBid().getMediaTypes(), ALLOWED_MEDIA_TYPES);
    }

    private static Imp makeImp(AdUnitBidWithParams<NormalizedPubmaticParams> adUnitBidWithParams,
                               PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final NormalizedPubmaticParams params = adUnitBidWithParams.getParams();

        final Set<MediaType> mediaTypes = allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES);
        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id(adUnitBid.getAdUnitCode())
                .instl(adUnitBid.getInstl())
                .secure(preBidRequestContext.getSecure())
                .tagid(mediaTypes.contains(MediaType.banner) && params != null ? params.getTagId() : null)
                .ext(params != null ? params.getKeywords() : null);
        if (mediaTypes.contains(MediaType.banner)) {
            impBuilder.banner(makeBanner(adUnitBid, params));
        }
        if (mediaTypes.contains(MediaType.video)) {
            impBuilder.video(videoBuilder(adUnitBid).build());
        }
        return impBuilder.build();
    }

    private static Banner makeBanner(AdUnitBid adUnitBid, NormalizedPubmaticParams params) {
        final List<Format> sizes = adUnitBid.getSizes();
        final Format format = sizes.get(0);
        return Banner.builder()
                .w(params != null ? params.getWidth() : format.getW())
                .h(params != null ? params.getHeight() : format.getH())
                .format(CollectionUtils.isNotEmpty(sizes) ? sizes : null) // pubmatic now supports format object
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
        final UidsCookie uidsCookie = preBidRequestContext.getUidsCookie();
        final String cookieValue = uidsCookie != null ? uidsCookie.uidFrom(cookieFamilyName) : null;

        return Cookie.cookie("KADUSERCOOKIE", ObjectUtils.firstNonNull(cookieValue, "")).encode();
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<BidRequest, BidResponse> exchangeCall) {
        return responseBidStream(exchangeCall.getResponse())
                .map(bid -> toBidBuilder(bid, adapterRequest, exchangeCall.getRequest().getImp()))
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdapterRequest adapterRequest,
                                               List<Imp> imps) {
        final String impId = bid.getImpid();
        final AdUnitBid adUnitBid = lookupBid(adapterRequest.getAdUnitBids(), impId);
        return Bid.builder()
                .bidder(adUnitBid.getBidderCode())
                .bidId(adUnitBid.getBidId())
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
                .mediaType(mediaTypeFor(imps, impId))
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid());
    }

    private static MediaType mediaTypeFor(List<Imp> imps, String impId) {
        return imps.stream()
                .filter(imp -> Objects.equals(imp.getId(), impId))
                .findAny()
                .map(PubmaticAdapter::mediaTypeFromImp)
                .orElse(MediaType.banner);
    }

    private static MediaType mediaTypeFromImp(Imp imp) {
        return imp.getVideo() != null ? MediaType.video : MediaType.banner;
    }
}
