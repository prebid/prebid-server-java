package org.prebid.server.bidder.impactify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.impactify.ExtImpImpactify;
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

public class ImpactifyBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpImpactify>> IMPACTIFY_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpImpactify>>() {
            };
    private static final String X_OPENRTB_VERSION = "2.5";
    private static final String BIDDER_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public ImpactifyBidder(String endpointUrl, JacksonMapper mapper, CurrencyConversionService conversionService) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.currencyConversionService = Objects.requireNonNull(conversionService);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> updatedImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final BigDecimal resolvedBidFloor = resolveBidFloor(request, imp);
                updatedImps.add(updateImp(imp, resolvedBidFloor));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest updatedBidRequest = updateBidRequest(request, updatedImps);

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(constructHeaders(updatedBidRequest))
                .body(mapper.encodeToBytes(updatedBidRequest))
                .payload(updatedBidRequest)
                .build());
    }

    private BigDecimal resolveBidFloor(BidRequest request, Imp imp) {
        return shouldConvertBidFloor(imp.getBidfloor(), imp.getBidfloorcur())
                ? convertBidFloorCurrency(imp.getBidfloor(), request, imp.getId(), imp.getBidfloorcur())
                : imp.getBidfloor();
    }

    private static boolean shouldConvertBidFloor(BigDecimal bidFloor, String bidFloorCur) {
        return BidderUtil.isValidPrice(bidFloor) && !StringUtils.equalsIgnoreCase(bidFloorCur, BIDDER_CURRENCY);
    }

    private BigDecimal convertBidFloorCurrency(BigDecimal bidFloor,
                                               BidRequest bidRequest,
                                               String impId,
                                               String bidFloorCur) {
        try {
            return currencyConversionService
                    .convertCurrency(bidFloor, bidRequest, bidFloorCur, BIDDER_CURRENCY);
        } catch (PreBidException e) {
            throw new PreBidException(String.format(
                    "Unable to convert provided bid floor currency from %s to %s for imp `%s`",
                    bidFloorCur, BIDDER_CURRENCY, impId));
        }
    }

    private Imp updateImp(Imp imp, BigDecimal bidFloor) {
        return imp.toBuilder()
                .bidfloorcur(BIDDER_CURRENCY)
                .bidfloor(bidFloor)
                .ext(mapper.mapper().createObjectNode()
                        .set("impactify", mapper.mapper().valueToTree(parseExtImp(imp))))
                .build();
    }

    private ExtImpImpactify parseExtImp(Imp imp) {
        try {
            return mapper.mapper()
                    .convertValue(imp.getExt(), IMPACTIFY_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Unable to decode the impression ext for id: %s", imp.getId()));
        }
    }

    private static BidRequest updateBidRequest(BidRequest request, List<Imp> updatedImps) {
        return request.toBuilder()
                .imp(updatedImps)
                .cur(Collections.singletonList(BIDDER_CURRENCY))
                .build();
    }

    private static MultiMap constructHeaders(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers().set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);

        final Device device = bidRequest.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());

            final String deviceIp = device.getIp();
            if (deviceIp != null) {
                HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, deviceIp);
            } else {
                HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            }
        }

        final Site site = bidRequest.getSite();
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }

        final User user = bidRequest.getUser();
        final String userUid = user != null ? user.getBuyeruid() : null;
        if (StringUtils.isNotBlank(userUid)) {
            headers.set(HttpUtil.COOKIE_HEADER, "uids=" + userUid);
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidRequest, bidResponse), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, bidRequest);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        throw new PreBidException(
                String.format("Failed to find a supported media type impression with ID: '%s'", impId));
    }
}
