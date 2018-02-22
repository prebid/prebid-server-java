package org.prebid.server.adapter.index;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.json.Json;
import org.prebid.server.adapter.OpenrtbAdapter;
import org.prebid.server.adapter.index.model.IndexParams;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    private final String endpointUrl;
    private final UsersyncInfo usersyncInfo;

    public IndexAdapter(String endpointUrl, String usersyncUrl) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.of(usersyncUrl, "redirect", false);
    }

    @Override
    public String code() {
        return "indexExchange";
    }

    @Override
    public String cookieFamily() {
        return "indexExchange";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }

    @Override
    public List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        validatePreBidRequest(preBidRequestContext.getPreBidRequest());

        final BidRequest bidRequest = createBidRequest(bidder, preBidRequestContext);
        final HttpRequest httpRequest = HttpRequest.of(endpointUrl, headers(), bidRequest);
        return Collections.singletonList(httpRequest);
    }

    private static void validatePreBidRequest(PreBidRequest preBidRequest) {
        if (preBidRequest.getApp() != null) {
            throw new PreBidException("Index doesn't support apps");
        }
    }

    private BidRequest createBidRequest(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = bidder.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids);

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
    public List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) {
        return responseBidStream(exchangeCall.getBidResponse())
                .map(bid -> toBidBuilder(bid, bidder))
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, Bidder bidder) {
        final AdUnitBid adUnitBid = lookupBid(bidder.getAdUnitBids(), bid.getImpid());
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
