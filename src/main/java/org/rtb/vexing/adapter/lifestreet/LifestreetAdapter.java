package org.rtb.vexing.adapter.lifestreet;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.json.Json;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.OpenrtbAdapter;
import org.rtb.vexing.adapter.lifestreet.model.LifestreetParams;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LifestreetAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    private final String endpointUrl;
    private final UsersyncInfo usersyncInfo;

    public LifestreetAdapter(String endpointUrl, String usersyncUrl, String externalUrl) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = encodeUrl("%s/setuid?bidder=lifestreet&uid=$$visitor_cookie$$",
                externalUrl);

        return UsersyncInfo.builder()
                .url(String.format("%s%s", usersyncUrl, redirectUri))
                .type("redirect")
                .supportCORS(false)
                .build();
    }

    @Override
    public String code() {
        return "Lifestreet";
    }

    @Override
    public String cookieFamily() {
        return "lifestreet";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }

    @Override
    public List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        validateAdUnitBidsMediaTypes(bidder.adUnitBids);

        return createAdUnitBidsWithParams(bidder.adUnitBids).stream()
                .flatMap(adUnitBidWithParams -> createBidRequests(adUnitBidWithParams, preBidRequestContext))
                .map(bidRequest -> HttpRequest.of(endpointUrl, headers(), bidRequest))
                .collect(Collectors.toList());
    }

    private static List<AdUnitBidWithParams> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static LifestreetParams parseAndValidateParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("Lifestreet params section is missing");
        }

        final LifestreetParams params;
        try {
            params = Json.mapper.convertValue(adUnitBid.params, LifestreetParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if (StringUtils.isEmpty(params.slotTag)) {
            throw new PreBidException("Missing slot_tag param");
        }
        if (params.slotTag.split("\\.").length != 2) {
            throw new PreBidException(String.format("Invalid slot_tag param '%s'", params.slotTag));
        }

        return params;
    }

    private Stream<BidRequest> createBidRequests(AdUnitBidWithParams adUnitBidWithParams,
                                                 PreBidRequestContext preBidRequestContext) {
        final List<Imp> imps = makeImps(adUnitBidWithParams, preBidRequestContext);
        validateImps(imps);

        return imps.stream()
                .map(imp -> BidRequest.builder()
                        .id(preBidRequestContext.preBidRequest.tid)
                        .at(1)
                        .tmax(preBidRequestContext.timeout)
                        .imp(Collections.singletonList(imp))
                        .app(preBidRequestContext.preBidRequest.app)
                        .site(makeSite(preBidRequestContext))
                        .device(deviceBuilder(preBidRequestContext).build())
                        .user(makeUser(preBidRequestContext))
                        .source(makeSource(preBidRequestContext))
                        .build());
    }

    private static List<Imp> makeImps(AdUnitBidWithParams adUnitBidWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.adUnitBid;
        final LifestreetParams params = adUnitBidWithParams.params;

        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(params.slotTag)
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
    public List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) {
        return responseBidStream(exchangeCall.bidResponse)
                .map(bid -> toBidBuilder(bid, bidder))
                .limit(1) // one bid per request/response
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
                .dealId(bid.getDealid())
                .nurl(bid.getNurl());
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class AdUnitBidWithParams {

        AdUnitBid adUnitBid;

        LifestreetParams params;
    }
}
