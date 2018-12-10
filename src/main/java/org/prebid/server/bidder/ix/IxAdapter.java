package org.prebid.server.bidder.ix;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.ix.proto.IxParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ix {@link Adapter} implementation.
 */
public class IxAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.singleton(MediaType.banner);

    // maximum number of bid requests
    private static final int REQUEST_LIMIT = 20;

    private final String endpointUrl;

    public IxAdapter(Usersyncer usersyncer, String endpointUrl) {
        super(usersyncer);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        validatePreBidRequest(preBidRequestContext.getPreBidRequest());

        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();
        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        final List<BidRequest> requests = makeRequests(adUnitBids, preBidRequestContext);
        if (CollectionUtils.isEmpty(requests)) {
            throw new PreBidException("Invalid ad unit/imp");
        }

        return requests.stream()
                .map(bidRequest -> AdapterHttpRequest.of(HttpMethod.POST, endpointUrl, bidRequest, headers()))
                .collect(Collectors.toList());
    }

    private static void validatePreBidRequest(PreBidRequest preBidRequest) {
        if (preBidRequest.getApp() != null) {
            throw new PreBidException("ix doesn't support apps");
        }
    }

    private List<BidRequest> makeRequests(List<AdUnitBid> adUnitBids, PreBidRequestContext preBidRequestContext) {
        final List<BidRequest> prioritizedRequests = new ArrayList<>();
        final List<BidRequest> regularRequests = new ArrayList<>();

        for (AdUnitBid adUnitBid : adUnitBids) {
            final Set<MediaType> mediaTypes = allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES);
            if (!mediaTypes.contains(MediaType.banner)) {
                continue;
            }
            final IxParams ixParams = parseAndValidateParams(adUnitBid);
            final List<Integer> ixParamsSizes = ixParams.getSize();
            boolean isFirstSize = true;
            for (Format format : adUnitBid.getSizes()) {
                if (CollectionUtils.isNotEmpty(ixParamsSizes) && !isValidIxSize(format, ixParamsSizes)) {
                    continue;
                }
                final BidRequest bidRequest = createBidRequest(adUnitBid, ixParams, format, preBidRequestContext);
                // prioritize slots over sizes
                if (isFirstSize) {
                    prioritizedRequests.add(bidRequest);
                    isFirstSize = false;
                } else {
                    regularRequests.add(bidRequest);
                }
            }
        }
        return Stream.concat(prioritizedRequests.stream(), regularRequests.stream())
                // cap the number of requests to requestLimit
                .limit(REQUEST_LIMIT)
                .collect(Collectors.toList());
    }

    private static IxParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("ix params section is missing");
        }

        final IxParams params;
        try {
            params = Json.mapper.convertValue(paramsNode, IxParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(String.format("unmarshal params '%s' failed: %s", paramsNode,
                    e.getMessage()), e.getCause());
        }

        final String siteId = params.getSiteId();
        if (StringUtils.isBlank(siteId)) {
            throw new PreBidException("Missing siteId param");
        }

        final List<Integer> size = params.getSize();
        if (size != null && size.size() < 2) {
            throw new PreBidException("Incorrect Size param: expected at least 2 values");
        }
        return params;
    }

    private static boolean isValidIxSize(Format format, List<Integer> sizes) {
        return Objects.equals(format.getW(), sizes.get(0)) && Objects.equals(format.getH(), sizes.get(1));
    }

    private BidRequest createBidRequest(AdUnitBid adUnitBid, IxParams ixParams, Format size,
                                        PreBidRequestContext preBidRequestContext) {
        final Imp imp = makeImp(copyAdUnitBidWithSingleSize(adUnitBid, size), preBidRequestContext);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(Collections.singletonList(imp))
                .site(makeSite(preBidRequestContext, ixParams.getSiteId()))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .build();
    }

    private static AdUnitBid copyAdUnitBidWithSingleSize(AdUnitBid adUnitBid, Format singleSize) {
        return adUnitBid.toBuilder().sizes(Collections.singletonList(singleSize)).build();
    }

    private static Imp makeImp(AdUnitBid adUnitBid, PreBidRequestContext preBidRequestContext) {
        final String adUnitCode = adUnitBid.getAdUnitCode();

        return Imp.builder()
                .id(adUnitCode)
                .instl(adUnitBid.getInstl())
                .banner(bannerBuilder(adUnitBid).build())
                .secure(preBidRequestContext.getSecure())
                .tagid(adUnitCode)
                .build();
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext, String siteId) {
        final Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        return siteBuilder == null ? null : siteBuilder
                .publisher(Publisher.builder().id(siteId).build())
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
                .dealId(bid.getDealid())
                .mediaType(MediaType.banner);
    }
}
