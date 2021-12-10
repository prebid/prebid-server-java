package org.prebid.server.bidder.adview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adview.ExtImpAdview;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdviewBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdview>> ADVIEW_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdview>>() {
            };
    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";
    private static final String BIDDER_CURRENCY = "USD";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public AdviewBidder(String endpointUrl,
                        CurrencyConversionService currencyConversionService,
                        JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Imp firstImp = request.getImp().get(0);
        final ExtImpAdview extImpAdview;
        final BidRequest modifiedBidRequest;

        try {
            extImpAdview = parseExtImp(firstImp);
            final BigDecimal resolvedBidFloor = resolveBidFloor(request, firstImp);
            final String resolvedBidFloorCurrency = resolveBidFloorCurrency(firstImp, resolvedBidFloor);
            modifiedBidRequest =
                    modifyRequest(request, extImpAdview.getMasterTagId(), resolvedBidFloor, resolvedBidFloorCurrency);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(resolveEndpoint(extImpAdview.getAccountId()))
                        .headers(HttpUtil.headers())
                        .body(mapper.encodeToBytes(modifiedBidRequest))
                        .payload(modifiedBidRequest)
                        .build());
    }

    private ExtImpAdview parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADVIEW_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext");
        }
    }

    private String resolveBidFloorCurrency(Imp firstImp, BigDecimal resolvedBidFloor) {
        final BigDecimal impBidFloor = firstImp.getBidfloor();

        if (impBidFloor != null && resolvedBidFloor != null && resolvedBidFloor.compareTo(impBidFloor) != 0) {
            return BIDDER_CURRENCY;
        }

        return firstImp.getBidfloorcur();
    }

    private BigDecimal resolveBidFloor(BidRequest request, Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        final String bidFloorCur = imp.getBidfloorcur();

        return shouldConvertBidFloor(bidFloor, bidFloorCur)
                ? convertBidFloorCurrency(bidFloor, bidFloorCur, imp.getId(), request)
                : bidFloor;
    }

    private static boolean shouldConvertBidFloor(BigDecimal bidFloor, String bidFloorCur) {
        return BidderUtil.isValidPrice(bidFloor) && !StringUtils.equalsIgnoreCase(bidFloorCur, BIDDER_CURRENCY);
    }

    private BigDecimal convertBidFloorCurrency(BigDecimal bidFloor,
                                               String bidFloorCur,
                                               String impId,
                                               BidRequest bidRequest) {
        try {
            return currencyConversionService
                    .convertCurrency(bidFloor, bidRequest, bidFloorCur, BIDDER_CURRENCY);
        } catch (PreBidException e) {
            throw new PreBidException(String.format(
                    "Unable to convert provided bid floor currency from %s to %s for imp `%s`",
                    bidFloorCur, BIDDER_CURRENCY, impId));
        }
    }

    private static BidRequest modifyRequest(BidRequest bidRequest,
                                            String masterTagId,
                                            BigDecimal resolvedBidFloor,
                                            String resolvedBidFloorCur) {
        return bidRequest.toBuilder()
                .imp(modifyImps(bidRequest.getImp(), masterTagId, resolvedBidFloor, resolvedBidFloorCur))
                .cur(Collections.singletonList(BIDDER_CURRENCY))
                .build();
    }

    private static List<Imp> modifyImps(List<Imp> imps,
                                        String masterTagId,
                                        BigDecimal resolvedBidFloor,
                                        String resolvedBidFloorCur) {
        final List<Imp> modifiedImps = new ArrayList<>(imps);
        modifiedImps.set(0, modifyImp(imps.get(0), masterTagId, resolvedBidFloor, resolvedBidFloorCur));
        return modifiedImps;
    }

    private static Imp modifyImp(Imp imp,
                                 String masterTagId,
                                 BigDecimal resolvedBidFloor,
                                 String resolvedBidFloorCur) {
        return imp.toBuilder()
                .tagid(masterTagId)
                .bidfloor(resolvedBidFloor)
                .bidfloorcur(resolvedBidFloorCur)
                .banner(resolveBanner(imp.getBanner()))
                .build();
    }

    private static Banner resolveBanner(Banner banner) {
        final List<Format> formats = banner != null ? banner.getFormat() : null;
        if (CollectionUtils.isNotEmpty(formats)) {
            final Format firstFormat = formats.get(0);
            return firstFormat != null
                    ? banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build()
                    : banner;
        }
        return banner;
    }

    private String resolveEndpoint(String accountId) {
        return endpointUrl.replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(accountId));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid.getImpid(), bidRequest.getImp()),
                        bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidMediaType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
