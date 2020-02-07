package org.prebid.server.bidder.lifestreet;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.lifestreet.proto.LifestreetParams;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lifestreet {@link Adapter} implementation.
 */
public class LifestreetAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LifestreetAdapter(String cookieFamilyName, String endpointUrl, JacksonMapper mapper) {
        super(cookieFamilyName);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        final List<BidRequest> requests = createAdUnitBidsWithParams(adUnitBids).stream()
                .filter(LifestreetAdapter::containsAnyAllowedMediaType)
                .map(adUnitBidWithParams -> createBidRequests(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(requests)) {
            throw new PreBidException("Invalid ad unit/imp");
        }

        return requests.stream()
                .map(bidRequest -> AdapterHttpRequest.of(HttpMethod.POST, endpointUrl, bidRequest, headers()))
                .collect(Collectors.toList());
    }

    private List<AdUnitBidWithParams<LifestreetParams>> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private LifestreetParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("Lifestreet params section is missing");
        }

        final LifestreetParams params;
        try {
            params = mapper.mapper().convertValue(paramsNode, LifestreetParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        final String slotTag = params.getSlotTag();
        if (StringUtils.isEmpty(slotTag)) {
            throw new PreBidException("Missing slot_tag param");
        }
        if (slotTag.split("\\.").length != 2) {
            throw new PreBidException(String.format("Invalid slot_tag param '%s'", slotTag));
        }

        return params;
    }

    private static boolean containsAnyAllowedMediaType(AdUnitBidWithParams<LifestreetParams> adUnitBidWithParams) {
        return CollectionUtils.containsAny(adUnitBidWithParams.getAdUnitBid().getMediaTypes(), ALLOWED_MEDIA_TYPES);
    }

    private BidRequest createBidRequests(AdUnitBidWithParams<LifestreetParams> adUnitBidWithParams,
                                         PreBidRequestContext preBidRequestContext) {
        final Imp imp = makeImp(adUnitBidWithParams, preBidRequestContext);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(Collections.singletonList(imp))
                .app(preBidRequest.getApp())
                .site(makeSite(preBidRequestContext))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .build();
    }

    private static Imp makeImp(AdUnitBidWithParams<LifestreetParams> adUnitBidWithParams,
                               PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final LifestreetParams params = adUnitBidWithParams.getParams();

        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id(adUnitBid.getAdUnitCode())
                .instl(adUnitBid.getInstl())
                .secure(preBidRequestContext.getSecure())
                .tagid(params.getSlotTag());

        final Set<MediaType> mediaTypes = allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES);
        if (mediaTypes.contains(MediaType.video)) {
            impBuilder.video(videoBuilder(adUnitBid).build());
        }
        if (mediaTypes.contains(MediaType.banner)) {
            impBuilder.banner(makeBanner(adUnitBid));
        }
        return impBuilder.build();
    }

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        return bannerBuilder(adUnitBid)
                .format(null)
                .build();
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<BidRequest, BidResponse> exchangeCall) {
        return responseBidStream(exchangeCall.getResponse())
                .map(bid -> toBidBuilder(bid, adapterRequest))
                .limit(1) // one bid per request/response
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
                .dealId(bid.getDealid())
                .nurl(bid.getNurl());
    }
}
