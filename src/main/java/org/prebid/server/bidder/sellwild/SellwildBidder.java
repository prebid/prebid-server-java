package org.prebid.server.bidder.sellwild;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.sellwild.ExtImpSellwild;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.Uri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SellwildBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSellwild>> SELLWILD_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PUBLISHER_ID_MACRO = "PublisherId";
    private static final String OPENRTB_VERSION = "2.6";

    private final Uri endpointUrl;
    private final JacksonMapper mapper;

    public SellwildBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Uri.of(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        // The exchange authenticates a request with the token of its first imp, so imps are only
        // grouped when they share both partner code and token.
        final Map<ExtImpSellwild, List<Imp>> paramsToImps = new LinkedHashMap<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpSellwild extImp = parseImpExt(imp);
                validateImpExt(extImp, imp.getId());
                paramsToImps.computeIfAbsent(extImp, key -> new ArrayList<>()).add(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final List<HttpRequest<BidRequest>> httpRequests = paramsToImps.entrySet().stream()
                .map(entry -> makeHttpRequest(request, entry.getKey().getPartnerCode(), entry.getValue()))
                .toList();

        return Result.of(httpRequests, errors);
    }

    private ExtImpSellwild parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SELLWILD_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to parse bidder parameters for imp %s: %s"
                    .formatted(imp.getId(), e.getMessage()));
        }
    }

    private static void validateImpExt(ExtImpSellwild extImp, String impId) {
        if (extImp == null) {
            throw new PreBidException("Missing bidder parameters for imp " + impId);
        }
        if (StringUtils.isBlank(extImp.getPartnerCode())) {
            throw new PreBidException("Missing partnerCode for imp " + impId);
        }
        if (StringUtils.isBlank(extImp.getToken())) {
            throw new PreBidException("Missing token for imp " + impId);
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, String partnerCode, List<Imp> imps) {
        final BidRequest outgoingRequest = request.toBuilder().imp(imps).build();
        final String uri = endpointUrl.replaceMacro(PUBLISHER_ID_MACRO, partnerCode).expand();

        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);

        return BidderUtil.defaultRequest(outgoingRequest, headers, uri, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bids = extractBids(bidResponse, httpCall.getRequest().getPayload(), errors);
            return Result.of(bids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse,
                                               BidRequest bidRequest,
                                               List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final Map<String, Imp> impIdToImp = bidRequest == null || bidRequest.getImp() == null
                ? Collections.emptyMap()
                : bidRequest.getImp().stream()
                        .collect(Collectors.toMap(Imp::getId, Function.identity(), (first, second) -> first));

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), impIdToImp, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid,
                                           String currency,
                                           Map<String, Imp> impIdToImp,
                                           List<BidderError> errors) {

        try {
            return BidderBid.of(bid, getBidType(bid, impIdToImp), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Bid bid, Map<String, Imp> impIdToImp) {
        final Imp imp = impIdToImp.get(bid.getImpid());
        if (imp == null) {
            throw new PreBidException("unable to find imp %s for bid %s".formatted(bid.getImpid(), bid.getId()));
        }

        final Integer markupType = bid.getMtype();
        if (BidderUtil.isNullOrZero(markupType)) {
            return getBidTypeFromImp(bid, imp);
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unsupported mtype %d for bid %s".formatted(markupType, bid.getId()));
        };
    }

    private static BidType getBidTypeFromImp(Bid bid, Imp imp) {
        final List<BidType> impTypes = new ArrayList<>();
        if (imp.getBanner() != null) {
            impTypes.add(BidType.banner);
        }
        if (imp.getVideo() != null) {
            impTypes.add(BidType.video);
        }
        if (imp.getXNative() != null) {
            impTypes.add(BidType.xNative);
        }

        if (impTypes.size() != 1) {
            throw new PreBidException(
                    "Unable to determine media type for bid %s: imp %s must have exactly one of banner, video, native"
                            .formatted(bid.getId(), imp.getId()));
        }

        return impTypes.getFirst();
    }
}
