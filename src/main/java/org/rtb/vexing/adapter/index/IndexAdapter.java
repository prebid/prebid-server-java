package org.rtb.vexing.adapter.index;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.json.Json;
import org.rtb.vexing.adapter.OpenrtbAdapter;
import org.rtb.vexing.adapter.index.model.IndexParams;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.UsersyncInfo;

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
        return UsersyncInfo.builder()
                .url(usersyncUrl)
                .type("redirect")
                .supportCORS(false)
                .build();
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
        validatePreBidRequest(preBidRequestContext.preBidRequest);

        final BidRequest bidRequest = createBidRequest(bidder, preBidRequestContext);
        final HttpRequest httpRequest = HttpRequest.of(endpointUrl, headers(), bidRequest);
        return Collections.singletonList(httpRequest);
    }

    private static void validatePreBidRequest(PreBidRequest preBidRequest) {
        if (preBidRequest.app != null) {
            throw new PreBidException("Index doesn't support apps");
        }
    }

    private BidRequest createBidRequest(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        validateAdUnitBidsMediaTypes(bidder.adUnitBids);

        final List<Imp> imps = makeImps(bidder.adUnitBids, preBidRequestContext);
        validateImps(imps);

        final Integer siteId = bidder.adUnitBids.stream()
                .map(IndexAdapter::parseAndValidateParams)
                .map(params -> params.siteId)
                .reduce((first, second) -> second).orElse(null);

        return BidRequest.builder()
                .id(preBidRequestContext.preBidRequest.tid)
                .at(1)
                .tmax(preBidRequestContext.timeout)
                .imp(imps)
                .site(makeSite(preBidRequestContext, siteId))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .build();
    }

    private static IndexParams parseAndValidateParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("IndexExchange params section is missing");
        }

        final IndexParams params;
        try {
            params = Json.mapper.convertValue(adUnitBid.params, IndexParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(String.format("unmarshal params '%s' failed: %s", adUnitBid.params,
                    e.getMessage()), e.getCause());
        }

        if (params.siteId == null || params.siteId == 0) {
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
        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(adUnitBid.adUnitCode)
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
        return responseBidStream(exchangeCall.bidResponse)
                .map(bid -> toBidBuilder(bid, bidder))
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, Bidder bidder) {
        final AdUnitBid adUnitBid = lookupBid(bidder.adUnitBids, bid.getImpid());
        return Bid.builder()
                .bidder(adUnitBid.bidderCode)
                .bidId(adUnitBid.bidId)
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid());
    }
}
