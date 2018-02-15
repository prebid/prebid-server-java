package org.rtb.vexing.adapter.appnexus;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.json.Json;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.OpenrtbAdapter;
import org.rtb.vexing.adapter.appnexus.model.AppnexusImpExt;
import org.rtb.vexing.adapter.appnexus.model.AppnexusImpExtAppnexus;
import org.rtb.vexing.adapter.appnexus.model.AppnexusKeyVal;
import org.rtb.vexing.adapter.appnexus.model.AppnexusParams;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AppnexusAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    private static final int AD_POSITION_ABOVE_THE_FOLD = 1; // openrtb.AdPosition.AdPositionAboveTheFold
    private static final int AD_POSITION_BELOW_THE_FOLD = 3; // openrtb.AdPosition.AdPositionBelowTheFold

    private final String endpointUrl;
    private final UsersyncInfo usersyncInfo;

    public AppnexusAdapter(String endpointUrl, String usersyncUrl, String externalUrl) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = encodeUrl("%s/setuid?bidder=adnxs&uid=$UID", externalUrl);

        return UsersyncInfo.builder()
                .url(String.format("%s%s", usersyncUrl, redirectUri))
                .type("redirect")
                .supportCORS(false)
                .build();
    }

    @Override
    public List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        final BidRequestWithUrl bidRequestWithUrl = createBidRequest(endpointUrl, bidder, preBidRequestContext);
        final HttpRequest httpRequest = HttpRequest.of(bidRequestWithUrl.endpointUrl, headers(),
                bidRequestWithUrl.bidRequest);
        return Collections.singletonList(httpRequest);
    }

    private BidRequestWithUrl createBidRequest(String endpointUrl, Bidder bidder,
                                               PreBidRequestContext preBidRequestContext) {
        validateAdUnitBidsMediaTypes(bidder.adUnitBids);

        final List<AdUnitBidWithParams> adUnitBidsWithParams = createAdUnitBidsWithParams(bidder.adUnitBids);
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        final BidRequest bidRequest = BidRequest.builder()
                .id(preBidRequestContext.preBidRequest.tid)
                .at(1)
                .tmax(preBidRequestContext.timeout)
                .imp(imps)
                .app(preBidRequestContext.preBidRequest.app)
                .site(makeSite(preBidRequestContext))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .build();

        return BidRequestWithUrl.of(bidRequest, endpointUrl(endpointUrl, adUnitBidsWithParams));
    }

    private static List<AdUnitBidWithParams> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static AppnexusParams parseAndValidateParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("Appnexus params section is missing");
        }

        final AppnexusParams params;
        try {
            params = DEFAULT_NAMING_MAPPER.convertValue(adUnitBid.params, AppnexusParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if (params.placementId == null || Objects.equals(params.placementId, 0)
                && (StringUtils.isEmpty(params.invCode) || StringUtils.isEmpty(params.member))) {
            throw new PreBidException("No placement or member+invcode provided");
        }
        return params;
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .flatMap(adUnitBidWithParams -> makeImpsForAdUnitBid(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static Stream<Imp> makeImpsForAdUnitBid(AdUnitBidWithParams adUnitBidWithParams,
                                                    PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.adUnitBid;
        final AppnexusParams params = adUnitBidWithParams.params;

        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid, params)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(StringUtils.stripToNull(params.invCode))
                        .bidfloor(bidfloor(params))
                        .ext(Json.mapper.valueToTree(makeImpExt(params)))
                        .build());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid, AppnexusParams params) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(videoBuilder(adUnitBid).build());
                break;
            case banner:
                impBuilder.banner(makeBanner(adUnitBid, params.position));
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
    }

    private static Banner makeBanner(AdUnitBid adUnitBid, String position) {
        final Banner.BannerBuilder bannerBuilder = bannerBuilder(adUnitBid);

        if (Objects.equals(position, "above")) {
            bannerBuilder.pos(AD_POSITION_ABOVE_THE_FOLD);
        } else if (Objects.equals(position, "below")) {
            bannerBuilder.pos(AD_POSITION_BELOW_THE_FOLD);
        }

        return bannerBuilder.build();
    }

    private static Float bidfloor(AppnexusParams params) {
        return params.reserve != null && params.reserve.compareTo(BigDecimal.ZERO) > 0
                ? params.reserve.floatValue() // TODO: we need to factor in currency here if non-USD
                : null;
    }

    private static AppnexusImpExt makeImpExt(AppnexusParams params) {
        return AppnexusImpExt.builder()
                .appnexus(AppnexusImpExtAppnexus.builder()
                        .placementId(params.placementId)
                        .keywords(makeKeywords(params))
                        .trafficSourceCode(params.trafficSourceCode)
                        .build())
                .build();
    }

    private static String makeKeywords(AppnexusParams params) {
        if (CollectionUtils.isEmpty(params.keywords)) {
            return null;
        }
        final List<String> kvs = new ArrayList<>();
        for (AppnexusKeyVal keyVal : params.keywords) {
            if (keyVal.values == null || keyVal.values.isEmpty()) {
                kvs.add(keyVal.key);
            } else {
                for (String value : keyVal.values) {
                    kvs.add(String.format("%s=%s", keyVal.key, value));
                }
            }
        }
        return kvs.stream().collect(Collectors.joining(","));
    }

    private static String endpointUrl(String endpointUrl, List<AdUnitBidWithParams> adUnitBidsWithParams) {
        return adUnitBidsWithParams.stream()
                .map(adUnitBidWithParams -> adUnitBidWithParams.params)
                .filter(params -> StringUtils.isNotEmpty(params.invCode) && StringUtils.isNotBlank(params.member))
                .reduce((first, second) -> second)
                .map(params -> String.format("%s%s", endpointUrl, String.format("?member_id=%s", params.member)))
                .orElse(endpointUrl);
    }

    @Override
    public String code() {
        return "appnexus";
    }

    @Override
    public String cookieFamily() {
        return "adnxs";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }

    @Override
    public List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) {
        return responseBidStream(exchangeCall.bidResponse)
                .map(bid -> toBidBuilder(bid, bidder, exchangeCall.bidRequest))
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, Bidder bidder, BidRequest bidRequest) {
        final AdUnitBid adUnitBid = lookupBid(bidder.adUnitBids, bid.getImpid());
        return Bid.builder()
                .bidder(adUnitBid.bidderCode)
                .bidId(adUnitBid.bidId)
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
                .mediaType(mediaTypeFor(bid.getImpid(), bidRequest.getImp()))
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid())
                .nurl(bid.getNurl());
    }

    private static MediaType mediaTypeFor(String impId, List<Imp> imps) {
        MediaType mediaType = MediaType.banner;
        if (imps != null) {
            for (Imp imp : imps) {
                if (Objects.equals(imp.getId(), impId)) {
                    if (imp.getVideo() != null) {
                        mediaType = MediaType.video;
                    }
                    break;
                }
            }
        }
        return mediaType;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class AdUnitBidWithParams {

        AdUnitBid adUnitBid;

        AppnexusParams params;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class BidRequestWithUrl {

        BidRequest bidRequest;

        String endpointUrl;
    }
}
