package org.prebid.server.bidder.pulsepoint;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.model.AdUnitBidWithParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.bidder.pulsepoint.model.NormalizedPulsepointParams;
import org.prebid.server.bidder.pulsepoint.proto.PulsepointParams;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pulsepoint {@link Adapter} implementation.
 */
public class PulsepointAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.singleton(MediaType.banner);

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public PulsepointAdapter(String cookieFamilyName, String endpointUrl, JacksonMapper mapper) {
        super(cookieFamilyName);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final BidRequest bidRequest = createBidRequest(adapterRequest, preBidRequestContext);
        final AdapterHttpRequest<BidRequest> httpRequest = AdapterHttpRequest.of(HttpMethod.POST, endpointUrl,
                bidRequest, HttpUtil.headers());
        return Collections.singletonList(httpRequest);
    }

    private BidRequest createBidRequest(AdapterRequest adapterRequest, PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        final List<AdUnitBidWithParams<NormalizedPulsepointParams>> adUnitBidsWithParams =
                createAdUnitBidsWithParams(adUnitBids);
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        final Publisher publisher = makePublisher(adUnitBidsWithParams);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
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

    private List<AdUnitBidWithParams<NormalizedPulsepointParams>> createAdUnitBidsWithParams(
            List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private NormalizedPulsepointParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("Pulsepoint params section is missing");
        }

        final PulsepointParams params;
        try {
            params = mapper.mapper().convertValue(paramsNode, PulsepointParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        final Integer publisherId = params.getPublisherId();
        if (publisherId == null || publisherId == 0) {
            throw new PreBidException("Missing PublisherId param cp");
        }
        final Integer tagId = params.getTagId();
        if (tagId == null || tagId == 0) {
            throw new PreBidException("Missing TagId param ct");
        }
        final String adSize = params.getAdSize();
        if (StringUtils.isEmpty(adSize)) {
            throw new PreBidException("Missing AdSize param cf");
        }

        final String[] sizes = adSize.toLowerCase().split("x");
        if (sizes.length != 2) {
            throw new PreBidException(String.format("Invalid AdSize param %s", adSize));
        }
        final int width;
        try {
            width = Integer.parseInt(sizes[0]);
        } catch (NumberFormatException e) {
            throw new PreBidException(String.format("Invalid Width param %s", sizes[0]));
        }

        final int height;
        try {
            height = Integer.parseInt(sizes[1]);
        } catch (NumberFormatException e) {
            throw new PreBidException(String.format("Invalid Height param %s", sizes[1]));
        }

        return NormalizedPulsepointParams.of(String.valueOf(publisherId), String.valueOf(tagId), width, height);
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams<NormalizedPulsepointParams>> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .filter(PulsepointAdapter::containsAnyAllowedMediaType)
                .map(adUnitBidWithParams -> makeImp(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static boolean containsAnyAllowedMediaType(
            AdUnitBidWithParams<NormalizedPulsepointParams> adUnitBidWithParams) {
        return CollectionUtils.containsAny(adUnitBidWithParams.getAdUnitBid().getMediaTypes(), ALLOWED_MEDIA_TYPES);
    }

    private static Imp makeImp(AdUnitBidWithParams<NormalizedPulsepointParams> adUnitBidWithParams,
                               PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final NormalizedPulsepointParams params = adUnitBidWithParams.getParams();

        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id(adUnitBid.getAdUnitCode())
                .instl(adUnitBid.getInstl())
                .secure(preBidRequestContext.getSecure())
                .tagid(params.getTagId());

        final Set<MediaType> mediaTypes = allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES);
        if (mediaTypes.contains(MediaType.banner)) {
            impBuilder.banner(makeBanner(adUnitBid, params.getAdSizeWidth(), params.getAdSizeHeight()));
        }
        return impBuilder.build();
    }

    private static Banner makeBanner(AdUnitBid adUnitBid, Integer width, Integer height) {
        return bannerBuilder(adUnitBid)
                .w(width)
                .h(height)
                .build();
    }

    private Publisher makePublisher(List<AdUnitBidWithParams<NormalizedPulsepointParams>> adUnitBidsWithParams) {
        final String publisherId = adUnitBidsWithParams.stream()
                .map(adUnitBidWithParams -> adUnitBidWithParams.getParams().getPublisherId())
                .reduce((first, second) -> second).orElse(null);
        return Publisher.builder().id(publisherId).build();
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
                .mediaType(MediaType.banner);
    }
}
