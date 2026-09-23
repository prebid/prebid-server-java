package org.prebid.server.bidder.openx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.openx.proto.OpenxBidExt;
import org.prebid.server.bidder.openx.proto.OpenxRequestExt;
import org.prebid.server.bidder.openx.proto.OpenxVideoExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.openx.ExtImpOpenx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenxBidder implements Bidder<BidRequest> {

    private static final String OPENX_CONFIG = "hb_pbs_1.0.0";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String CUSTOM_PARAMS_KEY = "customParams";
    private static final String BIDDER_EXT = "bidder";
    private static final String PREBID_EXT = "prebid";
    private static final Set<String> IMP_EXT_SKIP_FIELDS = Set.of(BIDDER_EXT, PREBID_EXT);

    private static final TypeReference<ExtPrebid<ExtImpPrebid, ExtImpOpenx>> OPENX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OpenxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        ExtImpOpenx firstValidImpExt = null;

        for (Imp imp : bidRequest.getImp()) {
            if (!isSupportedImpType(imp)) {
                errors.add(BidderError.badInput(
                        "OpenX only supports banner, video and native imps. Ignoring imp id=" + imp.getId()));
                continue;
            }

            final ExtPrebid<ExtImpPrebid, ExtImpOpenx> impExt;
            try {
                impExt = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput("imp id=%s: %s".formatted(imp.getId(), e.getMessage())));
                continue;
            }

            modifiedImps.add(makeImp(imp, impExt));
            if (firstValidImpExt == null) {
                firstValidImpExt = impExt.getBidder();
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedBidRequest = modifyBidRequest(bidRequest, modifiedImps, firstValidImpExt);

        return Result.of(
                Collections.singletonList(BidderUtil.defaultRequest(modifiedBidRequest, endpointUrl, mapper)),
                errors);
    }

    private static boolean isSupportedImpType(Imp imp) {
        return imp.getBanner() != null || imp.getVideo() != null || imp.getXNative() != null;
    }

    private ExtPrebid<ExtImpPrebid, ExtImpOpenx> parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), OPENX_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp makeImp(Imp imp, ExtPrebid<ExtImpPrebid, ExtImpOpenx> impExt) {
        final ExtImpOpenx openxImpExt = impExt.getBidder();
        final ExtImpPrebid prebidImpExt = impExt.getPrebid();

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .tagid(openxImpExt.getUnit())
                .bidfloor(resolveBidFloor(imp.getBidfloor(), openxImpExt.getCustomFloor()))
                .ext(makeImpExt(imp.getExt(), MapUtils.isNotEmpty(openxImpExt.getCustomParams())));

        if (imp.getVideo() != null
                && prebidImpExt != null
                && Objects.equals(prebidImpExt.getIsRewardedInventory(), 1)) {

            impBuilder.video(imp.getVideo().toBuilder()
                    .ext(mapper.mapper().valueToTree(OpenxVideoExt.of(1)))
                    .build());
        }

        return impBuilder.build();
    }

    private static BigDecimal resolveBidFloor(BigDecimal impBidFloor, BigDecimal customFloor) {
        return !BidderUtil.isValidPrice(impBidFloor) && BidderUtil.isValidPrice(customFloor)
                ? customFloor
                : impBidFloor;
    }

    private ObjectNode makeImpExt(ObjectNode impExt, boolean addCustomParams) {
        final ObjectNode openxImpExt = impExt.deepCopy();
        if (addCustomParams) {
            openxImpExt.set(CUSTOM_PARAMS_KEY, openxImpExt.get(BIDDER_EXT).get(CUSTOM_PARAMS_KEY));
        }
        openxImpExt.remove(IMP_EXT_SKIP_FIELDS);

        return openxImpExt;
    }

    private BidRequest modifyBidRequest(BidRequest bidRequest, List<Imp> imps, ExtImpOpenx openxImpExt) {
        return bidRequest.toBuilder()
                .imp(imps)
                .ext(makeReqExt(openxImpExt))
                .build();
    }

    private ExtRequest makeReqExt(ExtImpOpenx openxImpExt) {
        return mapper.fillExtension(
                ExtRequest.empty(),
                OpenxRequestExt.of(openxImpExt.getDelDomain(), openxImpExt.getPlatform(), OPENX_CONFIG));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidRequest, bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final Map<String, BidType> impIdToBidType = impIdToBidType(bidRequest);

        final String bidCurrency = StringUtils.defaultIfBlank(bidResponse.getCur(), DEFAULT_BID_CURRENCY);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> toBidderBid(bid, impIdToBidType, bidCurrency))
                .toList();
    }

    private static Map<String, BidType> impIdToBidType(BidRequest bidRequest) {
        return bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, OpenxBidder::resolveBidType));
    }

    private static BidType resolveBidType(Imp imp) {
        if (imp.getBanner() != null) {
            return BidType.banner;
        }
        if (imp.getVideo() != null) {
            return BidType.video;
        }
        if (imp.getXNative() != null) {
            return BidType.xNative;
        }
        return BidType.banner;
    }

    private BidderBid toBidderBid(Bid bid, Map<String, BidType> impIdToBidType, String bidCurrency) {
        final BidType bidType = getBidType(bid, impIdToBidType);
        final ExtBidPrebidVideo videoInfo = bidType == BidType.video ? getVideoInfo(bid) : null;

        return BidderBid.builder()
                .bid(bid.toBuilder().ext(getBidExt(bid)).build())
                .type(bidType)
                .bidCurrency(bidCurrency)
                .videoInfo(videoInfo)
                .build();
    }

    private static BidType getBidType(Bid bid, Map<String, BidType> impIdToBidType) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            case null, default -> impIdToBidType.getOrDefault(bid.getImpid(), BidType.banner);
        };
    }

    private static ExtBidPrebidVideo getVideoInfo(Bid bid) {
        return ExtBidPrebidVideo.of(
                bid.getDur(),
                CollectionUtils.isEmpty(bid.getCat()) ? null : bid.getCat().getFirst());
    }

    private ObjectNode getBidExt(Bid bid) {
        final ObjectNode ext = bid.getExt();
        if (ext == null) {
            return null;
        }

        final OpenxBidExt openxBidExt = parseOpenxBidExt(ext);
        final Integer buyerId = parseStringToInt(openxBidExt.getBuyerId());
        final Integer dspId = parseStringToInt(openxBidExt.getDspId());
        final Integer brandId = parseStringToInt(openxBidExt.getBrandId());

        if (buyerId == null && dspId == null && brandId == null) {
            return ext;
        }

        final ExtBidPrebidMeta meta = ExtBidPrebidMeta.builder()
                .networkId(dspId)
                .advertiserId(buyerId)
                .brandId(brandId)
                .build();

        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().meta(meta).build();

        ext.set(PREBID_EXT, mapper.mapper().valueToTree(extBidPrebid));

        return ext;
    }

    private OpenxBidExt parseOpenxBidExt(ObjectNode ext) {
        try {
            return mapper.mapper().convertValue(ext, OpenxBidExt.class);
        } catch (IllegalArgumentException e) {
            return OpenxBidExt.builder().build();
        }
    }

    private static Integer parseStringToInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
