package org.prebid.server.bidder.index;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.index.proto.IndexParams;
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
 * Index {@link Adapter} implementation.
 */
public class IndexAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    private final String endpointUrl;

    public IndexAdapter(Usersyncer usersyncer, String endpointUrl) {
        super(usersyncer);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        validatePreBidRequest(preBidRequestContext.getPreBidRequest());

        final BidRequest bidRequest = createBidRequest(adapterRequest, preBidRequestContext);
        final AdapterHttpRequest<BidRequest> httpRequest = AdapterHttpRequest.of(HttpMethod.POST, endpointUrl,
                bidRequest, headers());
        return Collections.singletonList(httpRequest);
    }

    private static void validatePreBidRequest(PreBidRequest preBidRequest) {
        if (preBidRequest.getApp() != null) {
            throw new PreBidException("Index doesn't support apps");
        }
    }

    private BidRequest createBidRequest(AdapterRequest adapterRequest, PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        final List<Imp> imps = makeImps(adUnitBids, preBidRequestContext);
        validateImps(imps);

        final Integer siteId = adUnitBids.stream()
                .map(IndexAdapter::parseAndValidateParams)
                .map(IndexParams::getSiteId)
                .reduce((first, second) -> second).orElse(null);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(imps)
                .site(makeSite(preBidRequestContext, siteId))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .build();
    }

    private static IndexParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("IndexExchange params section is missing");
        }

        final IndexParams params;
        try {
            params = Json.mapper.convertValue(paramsNode, IndexParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(String.format("unmarshal params '%s' failed: %s", paramsNode,
                    e.getMessage()), e.getCause());
        }

        final Integer siteId = params.getSiteId();
        if (siteId == null || siteId == 0) {
            throw new PreBidException("Missing siteID param");
        }

        return params;
    }

    private static List<Imp> makeImps(List<AdUnitBid> adUnitBids, PreBidRequestContext preBidRequestContext) {
        return adUnitBids.stream()
                .flatMap(adUnitBid -> makeImpsForAdUnitBid(adUnitBid, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static Stream<Imp> makeImpsForAdUnitBid(AdUnitBid adUnitBid, PreBidRequestContext preBidRequestContext) {
        final String adUnitCode = adUnitBid.getAdUnitCode();
        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid)
                        .id(adUnitCode)
                        .instl(adUnitBid.getInstl())
                        .secure(preBidRequestContext.getSecure())
                        .tagid(adUnitCode)
                        .build());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(videoBuilder(adUnitBid).build());
                break;
            case banner:
                impBuilder.banner(bannerBuilder(adUnitBid).build());
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext, Integer siteId) {
        final Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        return siteBuilder == null ? null : siteBuilder
                .publisher(Publisher.builder().id(siteId != null ? String.valueOf(siteId) : null).build())
                .build();
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
