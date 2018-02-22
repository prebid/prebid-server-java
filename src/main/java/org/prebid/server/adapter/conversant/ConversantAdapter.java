package org.prebid.server.adapter.conversant;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.adapter.OpenrtbAdapter;
import org.prebid.server.adapter.conversant.model.ConversantParams;
import org.prebid.server.adapter.model.AdUnitBidWithParams;
import org.prebid.server.adapter.model.ExchangeCall;
import org.prebid.server.adapter.model.HttpRequest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.AdUnitBid;
import org.prebid.server.model.Bidder;
import org.prebid.server.model.MediaType;
import org.prebid.server.model.PreBidRequestContext;
import org.prebid.server.model.request.PreBidRequest;
import org.prebid.server.model.response.Bid;
import org.prebid.server.model.response.UsersyncInfo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ConversantAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    // List of API frameworks supported by the publisher
    private static final Set<Integer> APIS = IntStream.range(1, 7).boxed().collect(Collectors.toSet());

    // Options for the various bid response protocols that could be supported by an exchange
    private static final Set<Integer> PROTOCOLS = IntStream.range(1, 11).boxed().collect(Collectors.toSet());

    // Position of the ad as a relative measure of visibility or prominence
    private static final Set<Integer> AD_POSITIONS = IntStream.range(0, 8).boxed().collect(Collectors.toSet());

    private final String endpointUrl;
    private final UsersyncInfo usersyncInfo;

    public ConversantAdapter(String endpointUrl, String usersyncUrl, String externalUrl) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = encodeUrl("%s/setuid?bidder=conversant&uid=", externalUrl);

        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    @Override
    public String code() {
        return "conversant";
    }

    @Override
    public String cookieFamily() {
        return "conversant";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }

    @Override
    public List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        if (preBidRequestContext.getPreBidRequest().getApp() == null
                && preBidRequestContext.getUidsCookie().uidFrom(cookieFamily()) == null) {
            return Collections.emptyList();
        }

        final BidRequest bidRequest = createBidRequest(bidder, preBidRequestContext);
        final HttpRequest httpRequest = HttpRequest.of(endpointUrl, headers(), bidRequest);
        return Collections.singletonList(httpRequest);
    }

    private BidRequest createBidRequest(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = bidder.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids);

        final List<AdUnitBidWithParams<ConversantParams>> adUnitBidsWithParams = createAdUnitBidsWithParams(adUnitBids);
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(imps)
                .app(preBidRequest.getApp())
                .site(makeSite(preBidRequestContext, adUnitBidsWithParams))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .build();
    }

    private static List<AdUnitBidWithParams<ConversantParams>> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static ConversantParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("Conversant params section is missing");
        }

        final ConversantParams params;
        try {
            params = Json.mapper.convertValue(paramsNode, ConversantParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if (StringUtils.isEmpty(params.getSiteId())) {
            throw new PreBidException("Missing site id");
        }

        return params;
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams<ConversantParams>> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .flatMap(adUnitBidWithParams -> makeImpsForAdUnitBid(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static Stream<Imp> makeImpsForAdUnitBid(AdUnitBidWithParams<ConversantParams> adUnitBidWithParams,
                                                    PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final ConversantParams params = adUnitBidWithParams.getParams();

        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid, params)
                        .id(adUnitBid.getAdUnitCode())
                        .instl(adUnitBid.getInstl())
                        .secure(makeSecure(preBidRequestContext, params))
                        .displaymanager("prebid-s2s")
                        .bidfloor(params.getBidfloor())
                        .tagid(params.getTagId())
                        .build());
    }

    private static Integer makeSecure(PreBidRequestContext preBidRequestContext, ConversantParams params) {
        // Take care not to override the global secure flag
        final Integer secure = preBidRequestContext.getSecure();
        final boolean validSecure = secure != null && secure != 0;
        final Integer secureInParams = params.getSecure();
        return !validSecure && secureInParams != null ? secureInParams : secure;
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid,
                                                      ConversantParams params) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(makeVideo(adUnitBid, params));
                break;
            case banner:
                impBuilder.banner(makeBanner(adUnitBid, params));
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
    }

    private static Video makeVideo(AdUnitBid adUnitBid, ConversantParams params) {
        final org.prebid.server.model.request.Video video = adUnitBid.getVideo();
        final List<String> mimes = params.getMimes();
        final Integer maxduration = params.getMaxduration();
        final Integer position = params.getPosition();
        return videoBuilder(adUnitBid)
                .mimes(mimes != null ? mimes : video.getMimes())
                .maxduration(maxduration != null ? maxduration : video.getMaxduration())
                .protocols(makeProtocols(params.getProtocols(), video.getProtocols()))
                .pos(AD_POSITIONS.contains(position) ? position : null)
                .api(makeApi(params.getApi()))
                .build();
    }

    private static List<Integer> makeApi(List<Integer> api) {
        return api == null ? null : api.stream().filter(APIS::contains).collect(Collectors.toList());
    }

    private static List<Integer> makeProtocols(List<Integer> paramsProtocols, List<Integer> adUnitBidProtocols) {
        return paramsProtocols == null ? adUnitBidProtocols : paramsProtocols.stream()
                .filter(PROTOCOLS::contains).collect(Collectors.toList());
    }

    private static Banner makeBanner(AdUnitBid adUnitBid, ConversantParams params) {
        return bannerBuilder(adUnitBid)
                .pos(params.getPosition())
                .build();
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext,
                                 List<AdUnitBidWithParams<ConversantParams>> adUnitBidsWithParams) {
        final Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        if (siteBuilder == null) {
            return null;
        }

        final String siteId = adUnitBidsWithParams.stream()
                .map(AdUnitBidWithParams::getParams)
                .filter(params -> params != null && StringUtils.isNotEmpty(params.getSiteId()))
                .map(ConversantParams::getSiteId)
                .reduce((first, second) -> second)
                .orElse(null);

        final Integer mobile = adUnitBidsWithParams.stream()
                .map(AdUnitBidWithParams::getParams)
                .filter(params -> params != null && params.getMobile() != null)
                .map(ConversantParams::getMobile)
                .reduce((first, second) -> second)
                .orElse(null);

        return siteBuilder
                .id(siteId)
                .mobile(mobile)
                .build();
    }

    @Override
    public List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) {
        final Map<String, Imp> impsMap = impsWithIds(exchangeCall.getBidRequest());

        return responseBidStream(exchangeCall.getBidResponse())
                .map(bid -> toBidBuilder(bid, bidder, impsMap.get(bid.getImpid())))
                .collect(Collectors.toList());
    }

    private static Map<String, Imp> impsWithIds(BidRequest bidRequest) {
        return bidRequest == null || bidRequest.getImp() == null ? Collections.emptyMap() : bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, Bidder bidder, Imp imp) {
        final AdUnitBid adUnitBid = lookupBid(bidder.getAdUnitBids(), bid.getImpid());
        final Bid.BidBuilder builder = Bid.builder()
                .bidder(bidder.getBidderCode())
                .bidId(adUnitBid.getBidId())
                .code(bid.getImpid())
                .price(bid.getPrice())
                .creativeId(bid.getCrid());

        if (imp != null) {
            if (imp.getVideo() != null) {
                builder
                        .mediaType(MediaType.video)
                        .nurl(bid.getAdm()) // Assign to NURL so it'll be interpreted as a vastUrl
                        .width(imp.getVideo().getW())
                        .height(imp.getVideo().getH());
            } else {
                builder
                        .mediaType(MediaType.banner)
                        .nurl(bid.getNurl())
                        .adm(bid.getAdm())
                        .width(bid.getW())
                        .height(bid.getH());
            }
        }

        return builder;
    }
}
