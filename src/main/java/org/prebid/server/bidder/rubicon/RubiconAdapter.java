package org.prebid.server.bidder.rubicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExt;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconDeviceExt;
import org.prebid.server.bidder.rubicon.proto.RubiconDeviceExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExt;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtRpTrack;
import org.prebid.server.bidder.rubicon.proto.RubiconParams;
import org.prebid.server.bidder.rubicon.proto.RubiconPubExt;
import org.prebid.server.bidder.rubicon.proto.RubiconPubExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconSiteExt;
import org.prebid.server.bidder.rubicon.proto.RubiconSiteExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconTargeting;
import org.prebid.server.bidder.rubicon.proto.RubiconTargetingExt;
import org.prebid.server.bidder.rubicon.proto.RubiconTargetingExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconUserExt;
import org.prebid.server.bidder.rubicon.proto.RubiconUserExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconVideoExt;
import org.prebid.server.bidder.rubicon.proto.RubiconVideoExtRp;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.request.Sdk;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <a href="https://rubiconproject.com">Rubicon Project</a> {@link Adapter} implementation.
 */
public class RubiconAdapter extends OpenrtbAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RubiconAdapter.class);

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";

    private final String endpointUrl;
    private final String authHeader;

    public RubiconAdapter(String cookieFamilyName, String endpointUrl, String xapiUsername, String xapiPassword) {
        super(cookieFamilyName);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        authHeader = "Basic " + Base64.getEncoder().encodeToString((Objects.requireNonNull(xapiUsername)
                + ':' + Objects.requireNonNull(xapiPassword)).getBytes());
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final MultiMap headers = headers()
                .add(HttpUtil.AUTHORIZATION_HEADER, authHeader)
                .add(HttpUtil.USER_AGENT_HEADER, PREBID_SERVER_USER_AGENT);

        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        final List<BidRequest> requests = adUnitBids.stream()
                .map(adUnitBid -> Tuple2.of(adUnitBid, mediaTypesFor(adUnitBid)))
                .filter(tuple2 -> CollectionUtils.isNotEmpty(tuple2.getRight())) // skip adUnitBid with no mediaType
                .map(tuple2 -> createBidRequests(tuple2.getLeft(), tuple2.getRight(), preBidRequestContext))
                .collect(Collectors.toList());

        validateBidRequests(requests);

        return requests.stream()
                .map(bidRequest -> AdapterHttpRequest.of(HttpMethod.POST, endpointUrl, bidRequest, headers))
                .collect(Collectors.toList());
    }

    private Set<MediaType> mediaTypesFor(AdUnitBid adUnitBid) {
        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .filter(mediaType -> isValidAdUnitBidMediaType(mediaType, adUnitBid))
                .collect(Collectors.toSet());
    }

    private static boolean isValidAdUnitBidMediaType(MediaType mediaType, AdUnitBid adUnitBid) {
        switch (mediaType) {
            case video:
                final org.prebid.server.proto.request.Video video = adUnitBid.getVideo();
                return video != null && !CollectionUtils.isEmpty(video.getMimes());
            case banner:
                return adUnitBid.getSizes().stream().map(RubiconSize::toId).anyMatch(id -> id > 0);
            default:
                return false;
        }
    }

    private BidRequest createBidRequests(AdUnitBid adUnitBid, Set<MediaType> mediaTypes,
                                         PreBidRequestContext preBidRequestContext) {
        final RubiconParams rubiconParams = parseAndValidateRubiconParams(adUnitBid);
        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .app(makeApp(rubiconParams, preBidRequestContext))
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(Collections.singletonList(makeImp(adUnitBid, mediaTypes, rubiconParams, preBidRequestContext)))
                .site(makeSite(rubiconParams, preBidRequestContext))
                .device(makeDevice(preBidRequestContext))
                .user(makeUser(rubiconParams, preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .build();
    }

    private RubiconParams parseAndValidateRubiconParams(AdUnitBid adUnitBid) {
        final ObjectNode params = adUnitBid.getParams();
        if (params == null) {
            throw new PreBidException("Rubicon params section is missing");
        }

        final RubiconParams rubiconParams;
        try {
            rubiconParams = Json.mapper.convertValue(params, RubiconParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        final Integer accountId = rubiconParams.getAccountId();
        final Integer siteId = rubiconParams.getSiteId();
        final Integer zoneId = rubiconParams.getZoneId();
        if (accountId == null || accountId == 0) {
            throw new PreBidException("Missing accountId param");
        } else if (siteId == null || siteId == 0) {
            throw new PreBidException("Missing siteId param");
        } else if (zoneId == null || zoneId == 0) {
            throw new PreBidException("Missing zoneId param");
        }

        return rubiconParams;
    }

    private static App makeApp(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        final App app = preBidRequestContext.getPreBidRequest().getApp();
        return app == null ? null : app.toBuilder()
                .publisher(makePublisher(rubiconParams))
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconParams)))
                .build();
    }

    private static Imp makeImp(AdUnitBid adUnitBid,
                               Set<MediaType> mediaTypes,
                               RubiconParams rubiconParams,
                               PreBidRequestContext preBidRequestContext) {
        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id(adUnitBid.getAdUnitCode())
                .secure(preBidRequestContext.getSecure())
                .instl(adUnitBid.getInstl())
                .ext(Json.mapper.valueToTree(makeImpExt(rubiconParams, preBidRequestContext)));

        if (mediaTypes.contains(MediaType.banner)) {
            impBuilder.banner(makeBanner(adUnitBid));
        }
        if (mediaTypes.contains(MediaType.video)) {
            impBuilder.video(makeVideo(adUnitBid, rubiconParams.getVideo()));
        }
        return impBuilder.build();
    }

    private static RubiconImpExt makeImpExt(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        return RubiconImpExt.of(RubiconImpExtRp.of(rubiconParams.getZoneId(), makeInventory(rubiconParams),
                makeImpExtRpTrack(preBidRequestContext)), null);
    }

    private static JsonNode makeInventory(RubiconParams rubiconParams) {
        final JsonNode inventory = rubiconParams.getInventory();
        return !inventory.isNull() ? inventory : null;
    }

    private static RubiconImpExtRpTrack makeImpExtRpTrack(PreBidRequestContext preBidRequestContext) {
        final Sdk sdk = preBidRequestContext.getPreBidRequest().getSdk();
        final String mintVersion;
        if (sdk != null) {
            mintVersion = String.format("%s_%s_%s", StringUtils.defaultString(sdk.getSource()),
                    StringUtils.defaultString(sdk.getPlatform()), StringUtils.defaultString(sdk.getVersion()));
        } else {
            mintVersion = "__";
        }

        return RubiconImpExtRpTrack.of("prebid", mintVersion);
    }

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        return bannerBuilder(adUnitBid)
                .ext(Json.mapper.valueToTree(makeBannerExt(adUnitBid.getSizes())))
                .build();
    }

    private static Video makeVideo(AdUnitBid adUnitBid, RubiconVideoParams rubiconVideoParams) {
        return videoBuilder(adUnitBid)
                .ext(rubiconVideoParams != null ? Json.mapper.valueToTree(makeVideoExt(rubiconVideoParams)) : null)
                .build();
    }

    private static RubiconVideoExt makeVideoExt(RubiconVideoParams rubiconVideoParams) {
        return RubiconVideoExt.of(rubiconVideoParams.getSkip(), rubiconVideoParams.getSkipdelay(),
                RubiconVideoExtRp.of(rubiconVideoParams.getSizeId()));
    }

    private static RubiconBannerExt makeBannerExt(List<Format> sizes) {
        final List<Integer> validRubiconSizeIds = sizes.stream()
                .map(RubiconSize::toId)
                .filter(id -> id > 0)
                .sorted(RubiconSize.comparator())
                .collect(Collectors.toList());

        return RubiconBannerExt.of(RubiconBannerExtRp.of(
                validRubiconSizeIds.get(0),
                validRubiconSizeIds.size() > 1 ? validRubiconSizeIds.subList(1, validRubiconSizeIds.size()) : null,
                "text/html"));
    }

    private Site makeSite(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        if (siteBuilder == null) {
            siteBuilder = Site.builder();
        }

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        if (preBidRequest.getApp() != null) {
            final User user = preBidRequest.getUser();
            final String language = user != null ? user.getLanguage() : null;
            siteBuilder
                    .content(Content.builder().language(language).build());
        } else {
            siteBuilder
                    .publisher(makePublisher(rubiconParams))
                    .ext(Json.mapper.valueToTree(makeSiteExt(rubiconParams)));
        }

        return siteBuilder.build();
    }

    private static RubiconSiteExt makeSiteExt(RubiconParams rubiconParams) {
        return RubiconSiteExt.of(RubiconSiteExtRp.of(rubiconParams.getSiteId()), null);
    }

    private static Publisher makePublisher(RubiconParams rubiconParams) {
        return Publisher.builder()
                .ext(Json.mapper.valueToTree(makePublisherExt(rubiconParams)))
                .build();
    }

    private static RubiconPubExt makePublisherExt(RubiconParams rubiconParams) {
        return RubiconPubExt.of(RubiconPubExtRp.of(rubiconParams.getAccountId()));
    }

    private static Device makeDevice(PreBidRequestContext preBidRequestContext) {
        return deviceBuilder(preBidRequestContext)
                .ext(Json.mapper.valueToTree(makeDeviceExt(preBidRequestContext)))
                .build();
    }

    private static RubiconDeviceExt makeDeviceExt(PreBidRequestContext preBidRequestContext) {
        final Device device = preBidRequestContext.getPreBidRequest().getDevice();
        final BigDecimal pixelratio = device != null ? device.getPxratio() : null;

        return RubiconDeviceExt.of(RubiconDeviceExtRp.of(pixelratio));
    }

    private User makeUser(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        User.UserBuilder userBuilder = userBuilder(preBidRequestContext);
        final User user = preBidRequestContext.getPreBidRequest().getUser();
        if (userBuilder == null) {
            userBuilder = user != null ? user.toBuilder() : User.builder();
        }
        final ObjectNode ext = user == null ? null : user.getExt();
        final ExtUser extUser = extUser(ext);
        final RubiconUserExt rubiconUserExt = makeUserExt(rubiconParams, user, extUser);
        return rubiconUserExt != null
                ? userBuilder.ext(Json.mapper.valueToTree(rubiconUserExt)).build()
                : userBuilder.build();
    }

    private static ExtUser extUser(ObjectNode extNode) {
        try {
            return extNode != null ? Json.mapper.treeToValue(extNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static RubiconUserExt makeUserExt(RubiconParams rubiconParams, User user, ExtUser extUser) {
        final ExtUserDigiTrust digiTrust = extUser != null ? extUser.getDigitrust() : null; // will be removed
        final JsonNode visitorNode = rubiconParams.getVisitor();
        final JsonNode visitor = !visitorNode.isNull() ? visitorNode : null;
        final String gender = user != null ? user.getGender() : null;
        final Integer yob = user != null ? user.getYob() : null;
        final Geo geo = user != null ? user.getGeo() : null;
        final boolean makeRp = visitor != null || gender != null || yob != null || geo != null;

        if (digiTrust != null || visitor != null || gender != null || yob != null || geo != null) {
            final RubiconUserExt.RubiconUserExtBuilder userExtBuilder = RubiconUserExt.builder();
            if (extUser != null) {
                userExtBuilder
                        .consent(extUser.getConsent())
                        .eids(extUser.getEids());
            }
            return userExtBuilder
                    .rp(makeRp ? RubiconUserExtRp.of(visitor, gender, yob, geo) : null)
                    .build();
        }
        return null;
    }

    private static void validateBidRequests(List<BidRequest> bidRequests) {
        if (bidRequests.isEmpty()) {
            throw new PreBidException("Invalid ad unit/imp");
        }
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<BidRequest, BidResponse> exchangeCall) {
        return responseBidStream(exchangeCall.getResponse())
                .filter(bid -> bid.getPrice() != null && bid.getPrice().compareTo(BigDecimal.ZERO) != 0)
                .map(bid -> toBidBuilder(bid, adapterRequest, mediaTypeFor(exchangeCall.getRequest())))
                .limit(1) // one bid per request/response
                .collect(Collectors.toList());
    }

    private static MediaType mediaTypeFor(BidRequest bidRequest) {
        final MediaType mediaType = MediaType.banner;
        if (bidRequest != null && CollectionUtils.isNotEmpty(bidRequest.getImp())
                && bidRequest.getImp().get(0).getVideo() != null) {
            return MediaType.video;
        }
        return mediaType;
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdapterRequest adapterRequest,
                                               MediaType mediaType) {
        final AdUnitBid adUnitBid = lookupBid(adapterRequest.getAdUnitBids(), bid.getImpid());
        return Bid.builder()
                .bidder(adUnitBid.getBidderCode())
                .bidId(adUnitBid.getBidId())
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
                .mediaType(mediaType)
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid())
                .adServerTargeting(toAdServerTargetingOrNull(bid));
    }

    private static Map<String, String> toAdServerTargetingOrNull(com.iab.openrtb.response.Bid bid) {
        RubiconTargetingExt rubiconTargetingExt = null;
        try {
            rubiconTargetingExt = Json.mapper.convertValue(bid.getExt(), RubiconTargetingExt.class);
        } catch (IllegalArgumentException e) {
            logger.warn("Exception occurred while de-serializing rubicon targeting extension", e);
        }

        final RubiconTargetingExtRp rp = rubiconTargetingExt != null ? rubiconTargetingExt.getRp() : null;
        final List<RubiconTargeting> targeting = rp != null ? rp.getTargeting() : null;
        return targeting != null
                ? targeting.stream().collect(Collectors.toMap(RubiconTargeting::getKey, t -> t.getValues().get(0)))
                : null;
    }

    @Override
    public boolean tolerateErrors() {
        return true;
    }
}
