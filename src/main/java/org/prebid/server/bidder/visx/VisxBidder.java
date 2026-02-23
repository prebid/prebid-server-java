package org.prebid.server.bidder.visx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.visx.model.VisxBid;
import org.prebid.server.bidder.visx.model.VisxResponse;
import org.prebid.server.bidder.visx.model.VisxSeatBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class VisxBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_REQUEST_CURRENCY = "USD";
    private static final Set<String> SUPPORTED_BID_TYPES_TEXTUAL = Set.of("banner", "video");

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ?>> BID_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public VisxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final BidRequest outgoingRequest = modifyRequest(request);
        return Result.withValue(
                BidderUtil.defaultRequest(outgoingRequest, makeHeaders(request.getDevice()), endpointUrl, mapper));
    }

    private static BidRequest modifyRequest(BidRequest bidRequest) {
        return CollectionUtils.isEmpty(bidRequest.getCur())
                ? bidRequest.toBuilder().cur(Collections.singletonList(DEFAULT_REQUEST_CURRENCY)).build()
                : bidRequest;
    }

    private static MultiMap makeHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final VisxResponse visxResponse = mapper.decodeValue(httpCall.getResponse().getBody(), VisxResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), visxResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, VisxResponse visxResponse) {
        if (visxResponse == null || CollectionUtils.isEmpty(visxResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, visxResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, VisxResponse visxResponse) {
        return visxResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(VisxSeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(visxBid -> toBidderBid(bidRequest, visxBid, visxResponse.getCur()))
                .toList();
    }

    private BidderBid toBidderBid(BidRequest bidRequest, VisxBid visxBid, String currency) {
        final Bid bid = toBid(visxBid, bidRequest.getId());
        final BidType bidType = getBidType(bid.getExt(), bid.getImpid(), bidRequest.getImp());
        return BidderBid.of(bid, bidType, StringUtils.defaultIfBlank(currency, null));
    }

    private static Bid toBid(VisxBid visxBid, String id) {
        return Bid.builder()
                .id(id)
                .impid(visxBid.getImpid())
                .price(visxBid.getPrice())
                .adm(visxBid.getAdm())
                .crid(visxBid.getCrid())
                .dealid(visxBid.getDealid())
                .h(visxBid.getH())
                .w(visxBid.getW())
                .adomain(visxBid.getAdomain())
                .ext(visxBid.getExt())
                .build();
    }

    private BidType getBidType(ObjectNode bidExt, String impId, List<Imp> imps) {
        final BidType extBidType = getBidTypeFromExt(bidExt);
        return extBidType != null ? extBidType : getBidTypeFromImp(impId, imps);
    }

    private BidType getBidTypeFromExt(ObjectNode bidExt) {
        try {
            return Optional.ofNullable(bidExt)
                    .map(ext -> mapper.mapper().convertValue(bidExt, BID_EXT_TYPE_REFERENCE))
                    .map(ExtPrebid::getPrebid)
                    .map(ExtBidPrebid::getMeta)
                    .map(ExtBidPrebidMeta::getMediaType)
                    .filter(SUPPORTED_BID_TYPES_TEXTUAL::contains)
                    .map(BidType::valueOf)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BidType getBidTypeFromImp(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                throw new PreBidException("Unknown impression type for ID: \"%s\"".formatted(impId));
            }
        }
        throw new PreBidException("Failed to find impression for ID: \"%s\"".formatted(impId));
    }
}
