package org.rtb.vexing.adapter.rubicon;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.OpenrtbAdapter;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRpTrack;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExtDt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoExtRP;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoParams;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.Sdk;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RubiconAdapter extends OpenrtbAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RubiconAdapter.class);

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";

    private final String endpointUrl;
    private final UsersyncInfo usersyncInfo;
    private final String authHeader;

    public RubiconAdapter(String endpointUrl, String usersyncUrl, String xapiUsername, String xapiPassword) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));

        authHeader = "Basic " + Base64.getEncoder().encodeToString((Objects.requireNonNull(xapiUsername)
                + ':' + Objects.requireNonNull(xapiPassword)).getBytes());
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.builder()
                .url(usersyncUrl)
                .type("redirect")
                .supportCORS(false)
                .build();
    }

    @Override
    public String code() {
        return "rubicon";
    }

    @Override
    public String cookieFamily() {
        return "rubicon";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }

    @Override
    public List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        final MultiMap headers = headers()
                .add(HttpHeaders.AUTHORIZATION, authHeader)
                .add(HttpHeaders.USER_AGENT, PREBID_SERVER_USER_AGENT);

        validateAdUnitBidsMediaTypes(bidder.adUnitBids);

        final List<HttpRequest> httpRequests = bidder.adUnitBids.stream()
                .flatMap(adUnitBid -> createBidRequests(adUnitBid, preBidRequestContext))
                .map(bidRequest -> HttpRequest.of(endpointUrl, headers, bidRequest))
                .collect(Collectors.toList());

        validateBidRequests(httpRequests.stream()
                .map(httpRequest -> httpRequest.bidRequest)
                .collect(Collectors.toList()));

        return httpRequests;
    }

    private static void validateBidRequests(List<BidRequest> bidRequests) {
        if (bidRequests.size() == 0) {
            throw new PreBidException("Invalid ad unit/imp");
        }
    }

    private Stream<BidRequest> createBidRequests(AdUnitBid adUnitBid, PreBidRequestContext preBidRequestContext) {
        final RubiconParams rubiconParams = parseAndValidateRubiconParams(adUnitBid);
        return makeImps(adUnitBid, rubiconParams, preBidRequestContext)
                .map(imp -> BidRequest.builder()
                        .id(preBidRequestContext.preBidRequest.tid)
                        .app(makeApp(rubiconParams, preBidRequestContext))
                        .at(1)
                        .tmax(preBidRequestContext.timeout)
                        .imp(Collections.singletonList(imp))
                        .site(makeSite(rubiconParams, preBidRequestContext))
                        .device(makeDevice(preBidRequestContext))
                        .user(makeUser(rubiconParams, preBidRequestContext))
                        .source(makeSource(preBidRequestContext))
                        .build());
    }

    private RubiconParams parseAndValidateRubiconParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("Rubicon params section is missing");
        }

        final RubiconParams rubiconParams;
        try {
            rubiconParams = DEFAULT_NAMING_MAPPER.convertValue(adUnitBid.params, RubiconParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if (rubiconParams.accountId == null || rubiconParams.accountId == 0) {
            throw new PreBidException("Missing accountId param");
        } else if (rubiconParams.siteId == null || rubiconParams.siteId == 0) {
            throw new PreBidException("Missing siteId param");
        } else if (rubiconParams.zoneId == null || rubiconParams.zoneId == 0) {
            throw new PreBidException("Missing zoneId param");
        }

        return rubiconParams;
    }

    private static App makeApp(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        final App app = preBidRequestContext.preBidRequest.app;
        return app == null ? null : app.toBuilder()
                .publisher(makePublisher(rubiconParams))
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconParams)))
                .build();
    }

    private static Stream<Imp> makeImps(AdUnitBid adUnitBid, RubiconParams rubiconParams,
                                        PreBidRequestContext preBidRequestContext) {
        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .filter(mediaType -> isValidAdUnitBidMediaType(mediaType, adUnitBid))
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid, rubiconParams))
                .map(impBuilder -> impBuilder
                        .id(adUnitBid.adUnitCode)
                        .secure(preBidRequestContext.secure)
                        .instl(adUnitBid.instl)
                        .ext(Json.mapper.valueToTree(makeImpExt(rubiconParams, preBidRequestContext)))
                        .build());
    }

    private static boolean isValidAdUnitBidMediaType(MediaType mediaType, AdUnitBid adUnitBid) {
        switch (mediaType) {
            case video:
                return adUnitBid.video != null && !CollectionUtils.isEmpty(adUnitBid.video.mimes);
            case banner:
                return adUnitBid.sizes.stream().map(RubiconSize::toId).anyMatch(id -> id > 0);
            default:
                return false;
        }
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid,
                                                      RubiconParams rubiconParams) {
        final Imp.ImpBuilder builder = Imp.builder();
        switch (mediaType) {
            case video:
                builder.video(makeVideo(adUnitBid, rubiconParams.video));
                break;
            case banner:
                builder.banner(makeBanner(adUnitBid));
                break;
            default:
                // unknown media type, just skip it
        }
        return builder;
    }

    private static RubiconImpExt makeImpExt(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        return RubiconImpExt.builder()
                .rp(RubiconImpExtRp.builder()
                        .zoneId(rubiconParams.zoneId)
                        .target(!rubiconParams.inventory.isNull() ? rubiconParams.inventory : null)
                        .track(makeImpExtRpTrack(preBidRequestContext))
                        .build())
                .build();
    }

    private static RubiconImpExtRpTrack makeImpExtRpTrack(PreBidRequestContext preBidRequestContext) {
        final Sdk sdk = preBidRequestContext.preBidRequest.sdk;
        final String mintVersion;
        if (sdk != null) {
            mintVersion = String.format("%s_%s_%s", StringUtils.defaultString(sdk.source),
                    StringUtils.defaultString(sdk.platform), StringUtils.defaultString(sdk.version));
        } else {
            mintVersion = "__";
        }

        return RubiconImpExtRpTrack.builder()
                .mint("prebid")
                .mintVersion(mintVersion)
                .build();
    }

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        return bannerBuilder(adUnitBid)
                .ext(Json.mapper.valueToTree(makeBannerExt(adUnitBid.sizes)))
                .build();
    }

    private static Video makeVideo(AdUnitBid adUnitBid, RubiconVideoParams rubiconVideoParams) {
        return videoBuilder(adUnitBid)
                .ext(rubiconVideoParams != null ? Json.mapper.valueToTree(makeVideoExt(rubiconVideoParams)) : null)
                .build();
    }

    private static RubiconVideoExt makeVideoExt(RubiconVideoParams rubiconVideoParams) {
        return RubiconVideoExt.builder()
                .skip(rubiconVideoParams.skip)
                .skipdelay(rubiconVideoParams.skipdelay)
                .rp(RubiconVideoExtRP.builder()
                        .sizeId(rubiconVideoParams.sizeId)
                        .build())
                .build();
    }

    private static RubiconBannerExt makeBannerExt(List<Format> sizes) {
        final List<Integer> validRubiconSizeIds = sizes.stream()
                .map(RubiconSize::toId)
                .filter(id -> id > 0)
                .collect(Collectors.toList());

        return RubiconBannerExt.builder()
                .rp(RubiconBannerExtRp.builder()
                        .sizeId(validRubiconSizeIds.get(0))
                        .altSizeIds(validRubiconSizeIds.size() > 1
                                ? validRubiconSizeIds.subList(1, validRubiconSizeIds.size()) : null)
                        .mime("text/html")
                        .build())
                .build();
    }

    private Site makeSite(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        if (siteBuilder == null) {
            siteBuilder = Site.builder();
        }

        if (preBidRequestContext.preBidRequest.app != null) {
            final User user = preBidRequestContext.preBidRequest.user;
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
        return RubiconSiteExt.builder()
                .rp(RubiconSiteExtRp.builder().siteId(rubiconParams.siteId).build())
                .build();
    }

    private static Publisher makePublisher(RubiconParams rubiconParams) {
        return Publisher.builder()
                .ext(Json.mapper.valueToTree(makePublisherExt(rubiconParams)))
                .build();
    }

    private static RubiconPubExt makePublisherExt(RubiconParams rubiconParams) {
        return RubiconPubExt.builder()
                .rp(RubiconPubExtRp.builder().accountId(rubiconParams.accountId).build())
                .build();
    }

    private static Device makeDevice(PreBidRequestContext preBidRequestContext) {
        return deviceBuilder(preBidRequestContext)
                .ext(Json.mapper.valueToTree(makeDeviceExt(preBidRequestContext)))
                .build();
    }

    private static RubiconDeviceExt makeDeviceExt(PreBidRequestContext preBidRequestContext) {
        final Device device = preBidRequestContext.preBidRequest.device;
        final BigDecimal pixelratio = device != null ? device.getPxratio() : null;

        return RubiconDeviceExt.builder()
                .rp(RubiconDeviceExtRp.builder().pixelratio(pixelratio).build())
                .build();
    }

    private User makeUser(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        User.UserBuilder userBuilder = userBuilder(preBidRequestContext);
        if (userBuilder == null) {
            final User user = preBidRequestContext.preBidRequest.user;
            userBuilder = user != null ? user.toBuilder() : User.builder();
        }

        return userBuilder
                .ext(Json.mapper.valueToTree(makeUserExt(rubiconParams, preBidRequestContext)))
                .build();
    }

    private static RubiconUserExt makeUserExt(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        if (rubiconParams.visitor.isNull() && preBidRequestContext.preBidRequest.digiTrust == null) {
            return null;
        }

        final RubiconUserExt.RubiconUserExtBuilder rubiconUserExtBuilder = RubiconUserExt.builder();

        if (!rubiconParams.visitor.isNull()) {
            rubiconUserExtBuilder.rp(RubiconUserExtRp.builder()
                    .target(rubiconParams.visitor).build());
        }

        // add DigiTrust id to Bid request only in case of preference equals to 0
        if (preBidRequestContext.preBidRequest.digiTrust != null
                && preBidRequestContext.preBidRequest.digiTrust.pref == 0) {
            rubiconUserExtBuilder.dt(RubiconUserExtDt.builder()
                    .id(preBidRequestContext.preBidRequest.digiTrust.id)
                    .keyv(preBidRequestContext.preBidRequest.digiTrust.keyv)
                    .preference(preBidRequestContext.preBidRequest.digiTrust.pref)
                    .build());
        }

        return rubiconUserExtBuilder.build();
    }

    @Override
    public List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) {
        return responseBidStream(exchangeCall.bidResponse)
                .filter(bid -> bid.getPrice() != null && bid.getPrice().compareTo(BigDecimal.ZERO) != 0)
                .map(bid -> toBidBuilder(bid, bidder, mediaTypeFor(exchangeCall.bidRequest)))
                .limit(1) // one bid per request/response
                .collect(Collectors.toList());
    }

    private static MediaType mediaTypeFor(BidRequest bidRequest) {
        MediaType mediaType = MediaType.banner;
        if (bidRequest != null && CollectionUtils.isNotEmpty(bidRequest.getImp())) {
            if (bidRequest.getImp().get(0).getVideo() != null) {
                return MediaType.video;
            }
        }
        return mediaType;
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, Bidder bidder, MediaType mediaType) {
        final AdUnitBid adUnitBid = lookupBid(bidder.adUnitBids, bid.getImpid());
        return Bid.builder()
                .bidder(adUnitBid.bidderCode)
                .bidId(adUnitBid.bidId)
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

        return rubiconTargetingExt != null && rubiconTargetingExt.rp != null && rubiconTargetingExt.rp.targeting != null
                ? rubiconTargetingExt.rp.targeting.stream().collect(Collectors.toMap(t -> t.key, t -> t.values.get(0)))
                : null;
    }

    @Override
    public boolean tolerateErrors() {
        return true;
    }
}
