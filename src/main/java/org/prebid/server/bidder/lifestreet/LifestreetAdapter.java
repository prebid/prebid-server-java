package org.prebid.server.bidder.lifestreet;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.lifestreet.proto.LifestreetParams;
import org.prebid.server.bidder.model.AdUnitBidWithParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
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
 * Lifestreet {@link Adapter} implementation.
 */
public class LifestreetAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    private final String endpointUrl;

    public LifestreetAdapter(Usersyncer usersyncer, String endpointUrl) {
        super(usersyncer);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        return createAdUnitBidsWithParams(adUnitBids).stream()
                .flatMap(adUnitBidWithParams -> createBidRequests(adUnitBidWithParams, preBidRequestContext))
                .map(bidRequest -> AdapterHttpRequest.of(HttpMethod.POST, endpointUrl, bidRequest, headers()))
                .collect(Collectors.toList());
    }

    private static List<AdUnitBidWithParams<LifestreetParams>> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static LifestreetParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("Lifestreet params section is missing");
        }

        final LifestreetParams params;
        try {
            params = Json.mapper.convertValue(paramsNode, LifestreetParams.class);
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

    private Stream<BidRequest> createBidRequests(AdUnitBidWithParams<LifestreetParams> adUnitBidWithParams,
                                                 PreBidRequestContext preBidRequestContext) {
        final List<Imp> imps = makeImps(adUnitBidWithParams, preBidRequestContext);
        validateImps(imps);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return imps.stream()
                .map(imp -> BidRequest.builder()
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
                        .build());
    }

    private static List<Imp> makeImps(AdUnitBidWithParams<LifestreetParams> adUnitBidWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final LifestreetParams params = adUnitBidWithParams.getParams();

        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid)
                        .id(adUnitBid.getAdUnitCode())
                        .instl(adUnitBid.getInstl())
                        .secure(preBidRequestContext.getSecure())
                        .tagid(params.getSlotTag())
                        .build())
                .collect(Collectors.toList());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(videoBuilder(adUnitBid).build());
                break;
            case banner:
                impBuilder.banner(makeBanner(adUnitBid));
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
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
