package org.prebid.server.bidder.mobilefuse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.mobilefuse.ExtImpMobilefuse;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MobilefuseBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMobilefuse>> MOBILEFUSE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String SKADN_PROPERTY_NAME = "skadn";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MobilefuseBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final String endpoint = request.getImp().stream()
                .map(this::parseImpExt)
                .filter(Objects::nonNull)
                .findFirst()
                .map(this::makeUrl)
                .orElse(null);

        if (endpoint == null) {
            return Result.withError(BidderError.badInput("Invalid ExtImpMobilefuse value"));
        }

        final List<Imp> modifiedImps = request.getImp().stream()
                .map(this::modifyImp)
                .filter(Objects::nonNull)
                .toList();

        if (modifiedImps.isEmpty()) {
            return Result.withError(BidderError.badInput("No valid imps"));
        }

        final BidRequest modifiedRequest = request.toBuilder().imp(modifiedImps).build();
        return Result.withValue(BidderUtil.defaultRequest(modifiedRequest, endpoint, mapper));
    }

    private Imp modifyImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null) {
            return null;
        }

        final ExtImpMobilefuse impExt = parseImpExt(imp);
        final ObjectNode skadn = parseSkadn(imp.getExt());
        return imp.toBuilder()
                .tagid(Objects.toString(impExt != null ? impExt.getPlacementId() : null, "0"))
                .ext(skadn != null ? mapper.mapper().createObjectNode().set(SKADN_PROPERTY_NAME, skadn) : null)
                .build();
    }

    private ExtImpMobilefuse parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), MOBILEFUSE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ObjectNode parseSkadn(ObjectNode impExt) {
        try {
            return mapper.mapper().convertValue(impExt.get(SKADN_PROPERTY_NAME), ObjectNode.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String makeUrl(ExtImpMobilefuse extImp) {
        final String baseUrl = endpointUrl + Objects.toString(extImp.getPublisherId(), "0");
        return "ext".equals(extImp.getTagidSrc()) ? baseUrl + "&tagid_src=ext" : baseUrl;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid.toBuilder().ext(null).build(), getBidType(bid), bidResponse.getCur()))
                .toList();
    }

    private BidType getBidType(Bid bid) {
        if (bid.getExt() == null) {
            return BidType.banner;
        }

        return switch (parseBidExtMediaType(bid.getExt())) {
            case "video" -> BidType.video;
            case "native" -> BidType.xNative;
            default -> BidType.banner;
        };
    }

    private String parseBidExtMediaType(ObjectNode bidExt) {
        try {
            return mapper.mapper().convertValue(bidExt.get("mf").get("media_type"), String.class);
        } catch (IllegalArgumentException e) {
            return "banner";
        }
    }
}
