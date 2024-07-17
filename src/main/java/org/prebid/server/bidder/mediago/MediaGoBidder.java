package org.prebid.server.bidder.mediago;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.mediago.MediaGoImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MediaGoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, MediaGoImpExt>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String BIDDER_NAME = "mediago";
    private static final String HOST_MACRO = "{{Host}}";
    private static final String ACCOUNT_ID_MACRO = "{{AccountID}}";
    private static final String X_OPENRTB_VERSION = "2.5";
    private static final String DEFAULT_REGION = "us";

    private static final Map<String, String> REGIONS_MAP = Map.of(
            "APAC", "jp",
            "EU", "eu",
            "US", "us");

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MediaGoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final MediaGoExt extRequest;
        try {
            extRequest = parseExt(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<Imp> modifiedImps = new ArrayList<>();
        for (Imp imp: request.getImp()) {
            final Imp modifiedImp = imp.toBuilder().banner(modifyBanner(imp.getBanner())).build();
            modifiedImps.add(modifiedImp);
        }

        final BidRequest modifiedBidRequest = request.toBuilder().imp(modifiedImps).build();
        final String modifiedEndpoint = resolveEndpoint(extRequest);
        return Result.withValue(BidderUtil.defaultRequest(modifiedBidRequest, makeHeaders(), modifiedEndpoint, mapper));
    }

    private MediaGoExt parseExt(BidRequest request) {
        final MediaGoExt ext = parseExt(request.getExt());

        if (ext != null && StringUtils.isNotBlank(ext.getToken())) {
            return ext;
        }

        final Imp firstImp = request.getImp().getFirst();
        final MediaGoImpExt impExt = parseImpExt(firstImp);

        if (StringUtils.isNotBlank(impExt.getToken())) {
            return MediaGoExt.of(impExt.getToken(), impExt.getRegion());
        }

        throw new PreBidException("mediago token not found");
    }

    private MediaGoExt parseExt(ExtRequest ext) {
        try {
            return Optional.ofNullable(ext)
                    .map(ExtRequest::getPrebid)
                    .map(ExtRequestPrebid::getBidderparams)
                    .map(bidders -> bidders.get(BIDDER_NAME))
                    .map(bidderParams -> mapper.mapper().convertValue(bidderParams, MediaGoExt.class))
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private MediaGoImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Banner modifyBanner(Banner banner) {
        if (banner == null) {
            return null;
        }

        final Integer width = banner.getW();
        final Integer height = banner.getH();
        final List<Format> formats = banner.getFormat();
        if ((width == null || width == 0 || height == null || height == 0) && CollectionUtils.isNotEmpty(formats)) {
            final Format firstFormat = formats.getFirst();
            return banner.toBuilder()
                    .w(firstFormat.getW())
                    .h(firstFormat.getH())
                    .build();
        }

        return banner;
    }

    private static MultiMap makeHeaders() {
        return HttpUtil.headers().set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
    }

    private String resolveEndpoint(MediaGoExt ext) {
        return endpointUrl
                .replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(ext.getToken()))
                .replace(HOST_MACRO, HttpUtil.encodeUrl(
                        REGIONS_MAP.getOrDefault(StringUtils.defaultString(ext.getRegion()), DEFAULT_REGION)));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, bidRequest, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();

    }

    private BidderBid makeBid(Bid bid, BidRequest bidRequest, String currency, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid, bidRequest.getImp());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        return getBidTypeFromMtype(bid.getMtype())
                .or(() -> getBidTypeFromImp(imps, bid.getImpid()))
                .orElseThrow(() -> new PreBidException("Unsupported MType " + bid.getMtype()));
    }

    private static Optional<BidType> getBidTypeFromMtype(Integer mType) {
        final BidType bidType = mType != null ? switch (mType) {
            case 1 -> BidType.banner;
            case 4 -> BidType.xNative;
            default -> null;
        } : null;

        return Optional.ofNullable(bidType);
    }

    private static Optional<BidType> getBidTypeFromImp(List<Imp> imps, String impId) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return Optional.of(BidType.banner);
                } else if (imp.getXNative() != null) {
                    return Optional.of(BidType.xNative);
                }
            }
        }
        return Optional.empty();
    }
}
