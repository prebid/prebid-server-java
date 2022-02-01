package org.prebid.server.bidder.algorix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.algorix.ExtImpAlgorix;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AlgoriX (@link Bidder) implementation.
 */
public class AlgorixBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAlgorix>> ALGORIX_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String URL_REGION_MACRO = "{HOST}";
    private static final String URL_SID_MACRO = "{SID}";
    private static final String URL_TOKEN_MACRO = "{TOKEN}";

    private static final int FIRST_INDEX = 0;

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private BidType bidType;

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
                extImpAlgorix = extImpAlgorix == null ? parseImpExt(imp) : extImpAlgorix;
                updatedImps.add(updateImp(imp));
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

    /**
     * Parse Ext Imp
     *
     * @param imp BidRequest Imp
     * @return Algorix Ext Imp
     */
    private ExtImpAlgorix parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ALGORIX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException error) {
            throw new PreBidException(String.format("Impression Id=%s, has invalid Ext", imp.getId()));
        }
    }

    /**
     * Update Imp for transform banner Size
     *
     * @param imp imp
     * @return new imp
     */
    private Imp updateImp(Imp imp) {
        if (imp.getBanner() != null) {
            final Banner banner = imp.getBanner();
            if (!(isValidSizeValue(banner.getW()) && isValidSizeValue(banner.getH()))
                    && CollectionUtils.isNotEmpty(banner.getFormat())) {
                final Format firstFormat = banner.getFormat().get(FIRST_INDEX);
                imp = imp.toBuilder()
                        .banner(banner.toBuilder()
                                .w(firstFormat.getW())
                                .h(firstFormat.getH())
                                .build())
                        .build();
            }
        }
        if (imp.getVideo() != null) {
            final ExtImpPrebid extImpPrebid = parseExtImp(imp);
            if (extImpPrebid != null && extImpPrebid.getIsRewardedInventory() == 1) {
                final ObjectNode rewardedJsonNode = mapper.mapper().createObjectNode().put("rewarded", 1);
                imp = imp.toBuilder()
                        .video(imp.getVideo().toBuilder()
                                .ext(rewardedJsonNode)
                                .build())
                        .build();
            }
        }
        return imp;
    }

    private ExtImpPrebid parseExtImp(Imp imp) {
        ExtImpPrebid extImpPrebid = null;
        try {
            extImpPrebid = mapper.mapper().treeToValue(imp.getExt(), ExtImp.class).getPrebid();
        } catch (JsonProcessingException e) {
        }
        return extImpPrebid;
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

    /**
     * get Region Info From Algorix Ext Imp
     * Default For Global EP, APAC for apse EP, USE for use EP
     *
     * @param extImp Algorix Ext Imp
     * @return Region String
     */
    private static String getRegionInfo(ExtImpAlgorix extImp) {
        if (Objects.isNull(extImp.getRegion())) {
            return "xyz";
        }
        switch (extImp.getRegion()) {
            case "APAC":
                return "apac.xyz";
            case "USE":
                return "use.xyz";
            default:
                return "xyz";
        }
    }

    /**
     * Replace url macro
     *
     * @param endpoint endpoint Url
     * @param extImp   Algorix Ext Imp
     * @return target Url
     */
    private static String resolveUrl(String endpoint, ExtImpAlgorix extImp) {
        return endpoint
                .replace(URL_REGION_MACRO, getRegionInfo(extImp))
                .replace(URL_SID_MACRO, extImp.getSid())
                .replace(URL_TOKEN_MACRO, extImp.getToken());
    }

    /**
     * Add openrtb version header 2.5
     *
     * @return headers
     */
    private static MultiMap resolveHeaders() {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
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
        return bidResponse.getSeatbid()
                .stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest.getImp()),
                        bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidType getBidType(Bid bid, List<Imp> imps) {
        final ObjectNode ext = bid.getExt();
        if (ext != null) {
            final JsonNode mediaTypeNode = ext.get("mediaType");
            final BidType bidType = mapper.mapper().convertValue(mediaTypeNode, BidType.class);

            return getBidTypeFromExt(bidType);
        }

        return getTypeFromImpId(bid, imps);
    }

    private BidType getTypeFromImpId(Bid bid, List<Imp> imps) {
        BidType mediaType = null;
        int typeCount = 0;
        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                if (imp.getBanner() != null) {
                    mediaType = BidType.banner;
                    typeCount++;
                }
                if (imp.getXNative() != null) {
                    mediaType = BidType.xNative;
                    typeCount++;
                }
                if (imp.getVideo() != null) {
                    mediaType = BidType.video;
                    typeCount++;
                }
            }
        }
        if (typeCount == 1) {
            return mediaType;
        }

        throw new PreBidException(String.format("Unable to fetch mediaType in multi-format: %s", bid.getImpid()));
    }

    private BidType getBidTypeFromExt(BidType bidType) {
        if (bidType.equals(BidType.banner)) {
            return BidType.banner;
        } else if (bidType.equals(BidType.xNative)) {
            return BidType.xNative;
        } else if (bidType.equals(BidType.video)) {
            return BidType.video;
        }
        return null;
    }
}
