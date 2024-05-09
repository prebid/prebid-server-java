package org.prebid.server.bidder.algorix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.algorix.model.AlgorixVideoExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.algorix.ExtImpAlgorix;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AlgoriX (@link Bidder) implementation.
 */
public class AlgorixBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<ExtImpPrebid, ExtImpAlgorix>> ALGORIX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String URL_REGION_MACRO = "{{HOST}}";
    private static final String URL_SID_MACRO = "{{SID}}";
    private static final String URL_TOKEN_MACRO = "{{TOKEN}}";

    private static final int FIRST_INDEX = 0;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AlgorixBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> updatedImps = new ArrayList<>();

        ExtImpAlgorix extImpAlgorix = null;

        for (Imp imp : request.getImp()) {
            try {
                final ExtPrebid<ExtImpPrebid, ExtImpAlgorix> impExt = parseImpExt(imp);
                extImpAlgorix = extImpAlgorix == null ? impExt.getBidder() : extImpAlgorix;
                updatedImps.add(updateImp(imp, impExt.getPrebid()));
            } catch (PreBidException error) {
                errors.add(BidderError.badInput(error.getMessage()));
            }
        }

        if (extImpAlgorix == null) {
            return Result.withError(BidderError.badInput("Invalid ExtImpAlgoriX value"));
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(updatedImps).build();
        return Result.of(Collections.singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(resolveUrl(endpointUrl, extImpAlgorix))
                                .headers(resolveHeaders())
                                .payload(outgoingRequest)
                                .body(mapper.encodeToBytes(outgoingRequest))
                                .build()),
                errors);
    }

    private ExtPrebid<ExtImpPrebid, ExtImpAlgorix> parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ALGORIX_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException error) {
            throw new PreBidException("Impression Id=%s, has invalid Ext".formatted(imp.getId()));
        }
    }

    private Imp updateImp(Imp imp, ExtImpPrebid extImpPrebid) {
        if (imp.getBanner() != null) {
            imp = updateBannerImp(imp);
        }
        if (imp.getVideo() != null) {
            imp = updateVideoImp(imp, extImpPrebid);
        }
        return imp;
    }

    private Imp updateBannerImp(Imp imp) {
        final Banner banner = imp.getBanner();
        if (!(isValidSizeValue(banner.getW()) && isValidSizeValue(banner.getH()))
                && CollectionUtils.isNotEmpty(banner.getFormat())) {
            final Format firstFormat = banner.getFormat().get(FIRST_INDEX);
            return imp.toBuilder()
                    .banner(banner.toBuilder()
                            .w(firstFormat.getW())
                            .h(firstFormat.getH())
                            .build())
                    .build();
        }
        return imp;
    }

    private Imp updateVideoImp(Imp imp, ExtImpPrebid extImpPrebid) {
        if (extImpPrebid != null && Objects.equals(extImpPrebid.getIsRewardedInventory(), 1)) {
            final Video video = imp.getVideo();
            return imp.toBuilder()
                    .video(video.toBuilder()
                            .ext(mapper.mapper().valueToTree(AlgorixVideoExt.of(1)))
                            .build())
                    .build();
        }
        return imp;
    }

    /**
     * Check Integer Size Value is Valid(not null and no zero)
     *
     * @param value Integer size value
     * @return true or false
     */
    private static boolean isValidSizeValue(Integer value) {
        return value != null && value > 0;
    }

    private static String getRegionInfo(ExtImpAlgorix extImp) {
        if (Objects.isNull(extImp.getRegion())) {
            return "xyz";
        }
        return switch (extImp.getRegion()) {
            case "APAC" -> "apac.xyz";
            case "USE" -> "use.xyz";
            case "EUC" -> "euc.xyz";
            default -> "xyz";
        };
    }

    private static String resolveUrl(String endpoint, ExtImpAlgorix extImp) {
        return endpoint
                .replace(URL_REGION_MACRO, getRegionInfo(extImp))
                .replace(URL_SID_MACRO, extImp.getSid())
                .replace(URL_TOKEN_MACRO, extImp.getToken());
    }

    private static MultiMap resolveHeaders() {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private BidType getBidType(Bid bid, List<Imp> imps) {
        final ObjectNode bidExt = bid.getExt();
        final JsonNode mediaTypeNode = bidExt != null ? bidExt.get("mediaType") : null;
        final String mediaType = mediaTypeNode != null && mediaTypeNode.isTextual()
                ? mediaTypeNode.textValue()
                : StringUtils.EMPTY;

        final BidType bidType = switch (mediaType) {
            case "banner" -> BidType.banner;
            case "native" -> BidType.xNative;
            case "video" -> BidType.video;
            default -> null;
        };
        if (bidType != null) {
            return bidType;
        }

        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
