package org.prebid.server.bidder.conversant;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.conversant.proto.ConversantParams;
import org.prebid.server.bidder.model.AdUnitBidWithParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Conversant {@link Adapter} implementation.
 */
public class ConversantAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    // List of API frameworks supported by the publisher
    private static final Set<Integer> APIS = IntStream.range(1, 7).boxed().collect(Collectors.toSet());

    // Options for the various bid response protocols that could be supported by an exchange
    private static final Set<Integer> PROTOCOLS = IntStream.range(1, 11).boxed().collect(Collectors.toSet());

    // Position of the ad as a relative measure of visibility or prominence
    private static final Set<Integer> AD_POSITIONS = IntStream.range(0, 8).boxed().collect(Collectors.toSet());

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ConversantAdapter(String cookieFamilyName, String endpointUrl, JacksonMapper mapper) {
        super(cookieFamilyName);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final BidRequest bidRequest = createBidRequest(adapterRequest, preBidRequestContext);
        final AdapterHttpRequest<BidRequest> httpRequest = AdapterHttpRequest.of(HttpMethod.POST, endpointUrl,
                bidRequest, headers());
        return Collections.singletonList(httpRequest);
    }

    private BidRequest createBidRequest(AdapterRequest adapterRequest, PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        final App app = preBidRequest.getApp();
        final List<AdUnitBidWithParams<ConversantParams>> adUnitBidsWithParams = createAdUnitBidsWithParams(adUnitBids,
                app);
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(imps)
                .app(app != null ? makeApp(adUnitBidsWithParams, app) : null)
                .site(makeSite(preBidRequestContext, adUnitBidsWithParams))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .build();
    }

    private List<AdUnitBidWithParams<ConversantParams>> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids,
                                                                                   App app) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid, app)))
                .collect(Collectors.toList());
    }

    private ConversantParams parseAndValidateParams(AdUnitBid adUnitBid, App app) {
        final boolean isAppRequest = app != null;
        if (isAppRequest && StringUtils.isBlank(app.getId())) {
            throw new PreBidException("Missing app id");
        }

        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("Conversant params section is missing");
        }

        final ConversantParams params;
        try {
            params = mapper.mapper().convertValue(paramsNode, ConversantParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if (!isAppRequest && StringUtils.isEmpty(params.getSiteId())) {
            throw new PreBidException("Missing site id");
        }

        return params;
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams<ConversantParams>> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .filter(ConversantAdapter::containsAnyAllowedMediaType)
                .map(adUnitBidWithParams -> makeImp(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static boolean containsAnyAllowedMediaType(AdUnitBidWithParams<ConversantParams> adUnitBidWithParams) {
        return CollectionUtils.containsAny(adUnitBidWithParams.getAdUnitBid().getMediaTypes(), ALLOWED_MEDIA_TYPES);
    }

    private static Imp makeImp(AdUnitBidWithParams<ConversantParams> adUnitBidWithParams,
                               PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final ConversantParams params = adUnitBidWithParams.getParams();

        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id(adUnitBid.getAdUnitCode())
                .instl(adUnitBid.getInstl())
                .secure(makeSecure(preBidRequestContext, params))
                .displaymanager("prebid-s2s")
                .displaymanagerver("1.0.1")
                .bidfloor(params.getBidfloor())
                .tagid(params.getTagId());

        final Set<MediaType> mediaTypes = allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES);
        if (mediaTypes.contains(MediaType.banner)) {
            impBuilder.banner(makeBanner(adUnitBid, params));
        }
        if (mediaTypes.contains(MediaType.video)) {
            impBuilder.video(makeVideo(adUnitBid, params));
        }

        return impBuilder.build();
    }

    private static Integer makeSecure(PreBidRequestContext preBidRequestContext, ConversantParams params) {
        // Take care not to override the global secure flag
        final Integer secure = preBidRequestContext.getSecure();
        final boolean validSecure = secure != null && secure != 0;
        final Integer secureInParams = params.getSecure();
        return !validSecure && secureInParams != null ? secureInParams : secure;
    }

    private static Video makeVideo(AdUnitBid adUnitBid, ConversantParams params) {
        final org.prebid.server.proto.request.Video video = adUnitBid.getVideo();
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

    private static App makeApp(List<AdUnitBidWithParams<ConversantParams>> adUnitBidsWithParams, App app) {
        final String siteId = adUnitBidsWithParams.stream()
                .map(AdUnitBidWithParams::getParams)
                .filter(params -> params != null && StringUtils.isNotEmpty(params.getSiteId()))
                .map(ConversantParams::getSiteId)
                .reduce((first, second) -> second)
                .orElse(null);

        if (StringUtils.isNotBlank(siteId)) {
            return app.toBuilder()
                    .id(siteId)
                    .build();
        }

        return app;
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
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<BidRequest, BidResponse> exchangeCall) {
        final Map<String, Imp> impsMap = impsWithIds(exchangeCall.getRequest());

        return responseBidStream(exchangeCall.getResponse())
                .map(bid -> toBidBuilder(bid, adapterRequest, impsMap.get(bid.getImpid())))
                .collect(Collectors.toList());
    }

    private static Map<String, Imp> impsWithIds(BidRequest bidRequest) {
        return bidRequest == null || bidRequest.getImp() == null ? Collections.emptyMap() : bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdapterRequest adapterRequest,
                                               Imp imp) {
        final AdUnitBid adUnitBid = lookupBid(adapterRequest.getAdUnitBids(), bid.getImpid());
        final Bid.BidBuilder builder = Bid.builder()
                .bidder(adapterRequest.getBidderCode())
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
