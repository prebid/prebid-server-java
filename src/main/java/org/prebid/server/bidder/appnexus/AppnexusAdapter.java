package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.appnexus.model.BidRequestWithUrl;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.appnexus.proto.AppnexusParams;
import org.prebid.server.bidder.model.AdUnitBidWithParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AppNexus {@link Adapter} implementation.
 */
public class AppnexusAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    private static final int AD_POSITION_ABOVE_THE_FOLD = 1; // openrtb.AdPosition.AdPositionAboveTheFold
    private static final int AD_POSITION_BELOW_THE_FOLD = 3; // openrtb.AdPosition.AdPositionBelowTheFold

    private final String endpointUrl;

    public AppnexusAdapter(Usersyncer usersyncer, String endpointUrl) {
        super(usersyncer);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final BidRequestWithUrl bidRequestWithUrl = createBidRequest(endpointUrl, adapterRequest, preBidRequestContext);
        final AdapterHttpRequest<BidRequest> httpRequest = AdapterHttpRequest.of(HttpMethod.POST,
                bidRequestWithUrl.getEndpointUrl(), bidRequestWithUrl.getBidRequest(), headers());
        return Collections.singletonList(httpRequest);
    }

    private BidRequestWithUrl createBidRequest(String endpointUrl, AdapterRequest adapterRequest,
                                               PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);

        final List<AdUnitBidWithParams<AppnexusParams>> adUnitBidsWithParams = createAdUnitBidsWithParams(adUnitBids);
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        final BidRequest bidRequest = BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(imps)
                .app(preBidRequest.getApp())
                .site(makeSite(preBidRequestContext))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .build();

        return BidRequestWithUrl.of(bidRequest, endpointUrl(endpointUrl, adUnitBidsWithParams));
    }

    private static List<AdUnitBidWithParams<AppnexusParams>> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static AppnexusParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("Appnexus params section is missing");
        }

        AppnexusParams params;
        try {
            params = Json.mapper.convertValue(paramsNode, AppnexusParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        // Accept legacy Appnexus parameters if we don't have modern ones
        // Don't worry if both is set as validation rules should prevent, and this is temporary anyway.
        boolean setPlacementId = params.getPlacementId() == null && params.getLegacyPlacementId() != null;
        boolean setInvCode = params.getInvCode() == null && params.getLegacyInvCode() != null;
        boolean setTrafficSourceCode = params.getTrafficSourceCode() == null
                && params.getLegacyTrafficSourceCode() != null;
        if (setPlacementId || setInvCode || setTrafficSourceCode) {
            params = params.toBuilder()
                    .placementId(setPlacementId ? params.getLegacyPlacementId() : params.getPlacementId())
                    .invCode(setInvCode ? params.getLegacyInvCode() : params.getInvCode())
                    .trafficSourceCode(setTrafficSourceCode ? params.getLegacyTrafficSourceCode()
                            : params.getTrafficSourceCode())
                    .build();
        }

        final Integer placementId = params.getPlacementId();
        if (placementId == null || Objects.equals(placementId, 0)
                && (StringUtils.isEmpty(params.getInvCode()) || StringUtils.isEmpty(params.getMember()))) {
            throw new PreBidException("No placement or member+invcode provided");
        }

        return params;
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams<AppnexusParams>> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .filter(AppnexusAdapter::containsAnyAllowedMediaType)
                .map(adUnitBidWithParams -> makeImp(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static boolean containsAnyAllowedMediaType(AdUnitBidWithParams<AppnexusParams> adUnitBidWithParams) {
        return CollectionUtils.containsAny(adUnitBidWithParams.getAdUnitBid().getMediaTypes(), ALLOWED_MEDIA_TYPES);
    }

    private static Imp makeImp(AdUnitBidWithParams<AppnexusParams> adUnitBidWithParams,
                               PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();
        final AppnexusParams params = adUnitBidWithParams.getParams();

        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id(adUnitBid.getAdUnitCode())
                .instl(adUnitBid.getInstl())
                .secure(preBidRequestContext.getSecure())
                .tagid(StringUtils.stripToNull(params.getInvCode()))
                .bidfloor(bidfloor(params))
                .ext(Json.mapper.valueToTree(makeImpExt(params)));

        final Set<MediaType> mediaTypes = allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES);
        if (mediaTypes.contains(MediaType.banner)) {
            impBuilder.banner(makeBanner(adUnitBid, params.getPosition()));
        }
        if (mediaTypes.contains(MediaType.video)) {
            impBuilder.video(videoBuilder(adUnitBid).build());
        }

        return impBuilder.build();
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

    private static BigDecimal bidfloor(AppnexusParams params) {
        final BigDecimal reserve = params.getReserve();
        return reserve != null && reserve.compareTo(BigDecimal.ZERO) > 0
                ? reserve
                : null;
    }

    private static AppnexusImpExt makeImpExt(AppnexusParams params) {
        return AppnexusImpExt.of(
                AppnexusImpExtAppnexus.of(params.getPlacementId(), makeKeywords(params), params.getTrafficSourceCode(),
                        params.getUsePmtRule(), params.getPrivateSizes()));
    }

    private static String makeKeywords(AppnexusParams params) {
        final List<AppnexusKeyVal> keywords = params.getKeywords();
        if (CollectionUtils.isEmpty(keywords)) {
            return null;
        }

        final List<String> kvs = new ArrayList<>();
        for (AppnexusKeyVal keyVal : keywords) {
            final String key = keyVal.getKey();
            final List<String> values = keyVal.getValue();
            if (values == null || values.isEmpty()) {
                kvs.add(key);
            } else {
                for (String value : values) {
                    kvs.add(String.format("%s=%s", key, value));
                }
            }
        }

        return kvs.stream().collect(Collectors.joining(","));
    }

    private static String endpointUrl(String endpointUrl,
                                      List<AdUnitBidWithParams<AppnexusParams>> adUnitBidsWithParams) {
        return adUnitBidsWithParams.stream()
                .map(AdUnitBidWithParams::getParams)
                .filter(params -> StringUtils.isNotEmpty(params.getInvCode())
                        && StringUtils.isNotBlank(params.getMember()))
                .reduce((first, second) -> second)
                .map(params -> String.format("%s%s", endpointUrl, String.format("?member_id=%s", params.getMember())))
                .orElse(endpointUrl);
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
                .mediaType(mediaTypeFor(bid.getExt()))
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid())
                .nurl(bid.getNurl());
    }

    private static MediaType mediaTypeFor(ObjectNode bidExt) {
        final AppnexusBidExtAppnexus appnexus = parseAppnexusBidExt(bidExt).getAppnexus();
        if (appnexus == null) {
            throw new PreBidException("bidResponse.bid.ext.appnexus should be defined");
        }

        final Integer bidAdType = appnexus.getBidAdType();

        if (bidAdType == null) {
            throw new PreBidException("bidResponse.bid.ext.appnexus.bid_ad_type should be defined");
        }

        switch (bidAdType) {
            case 0:
                return MediaType.banner;
            case 1:
                return MediaType.video;
            default:
                throw new PreBidException(
                        String.format("Unrecognized bid_ad_type in response from appnexus: %s", bidAdType));
        }
    }

    private static AppnexusBidExt parseAppnexusBidExt(ObjectNode bidExt) {
        if (bidExt == null) {
            throw new PreBidException("bidResponse.bid.ext should be defined for appnexus");
        }

        final AppnexusBidExt appnexusBidExt;
        try {
            appnexusBidExt = Json.mapper.treeToValue(bidExt, AppnexusBidExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
        return appnexusBidExt;
    }

}
