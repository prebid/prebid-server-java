package org.prebid.server.bidder.synapsehx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.client.utils.URIBuilder;
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
import org.prebid.server.proto.openrtb.ext.request.synapsehx.ExtImpSynapseHX;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SynapseHXBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSynapseHX>> SYNAPSE_HX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ?>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String OPENRTB_VERSION = "2.6";

    private final String endpoint;
    private final JacksonMapper mapper;

    public SynapseHXBidder(String endpoint, JacksonMapper mapper) {
        this.endpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (bidRequest.getImp().isEmpty()) {
            return Result.withError(BidderError.badInput("Request has no imps"));
        }

        final Imp firstImp = bidRequest.getImp().getFirst();
        final String uri;

        try {
            final String tenantId = getTenantId(firstImp);
            uri = makeUri(tenantId);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final MultiMap headers = HttpUtil.headers();

        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);

        return Result.withValue(BidderUtil.defaultRequest(bidRequest, headers, uri, mapper));
    }

    private String getTenantId(Imp firstImp) {
        try {
            return mapper.mapper()
                    .convertValue(firstImp.getExt(), SYNAPSE_HX_EXT_TYPE_REFERENCE)
                    .getBidder()
                    .getTenantId();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to parse bidder parameters: %s".formatted(e.getMessage()));
        }
    }

    private String makeUri(String tenantId) {
        try {
            return new URIBuilder(endpoint).addParameter("pid", tenantId).toString();
        } catch (URISyntaxException e) {
            throw new PreBidException("Invalid endpoint URI: %s".formatted(e.getMessage()));
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> bidderErrors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, bidderErrors), bidderErrors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> bidderErrors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, bidderErrors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> bidderErrors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> makeBidderBid(bid, bidResponse, bidderErrors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBidderBid(Bid bid, BidResponse bidResponse, List<BidderError> bidderErrors) {
        final BidType bidType = getBidType(bid, bidderErrors);
        return bidType == null
                ? null
                : BidderBid.of(bid, bidType, bidResponse.getCur());
    }

    private BidType getBidType(Bid bid, List<BidderError> errors) {
        final Integer mType = bid.getMtype();
        return mType != null
                ? getBidTypeFromMType(mType, errors)
                : getBidTypeFromExt(bid.getExt(), errors);
    }

    private static BidType getBidTypeFromMType(Integer mType, List<BidderError> errors) {
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> {
                errors.add(BidderError.badServerResponse("Unsupported media type: %d".formatted(mType)));
                yield null;
            }
        };
    }

    private BidType getBidTypeFromExt(ObjectNode bidExt, List<BidderError> errors) {
        final BidType bidType = Optional.ofNullable(bidExt)
                .map(ext -> mapper.mapper().convertValue(ext, EXT_PREBID_TYPE_REFERENCE))
                .map(ExtPrebid::getPrebid)
                .map(ExtBidPrebid::getType)
                .orElse(null);

        return switch (bidType) {
            case banner -> BidType.banner;
            case video -> BidType.video;
            case null, default -> {
                errors.add(BidderError.badServerResponse("Unsupported media type: %s".formatted(bidType)));
                yield null;
            }
        };
    }

}
