package org.prebid.server.bidder.synapsehx;

import com.fasterxml.jackson.core.type.TypeReference;
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
            new TypeReference<>() { };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ?>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() { };

    private static final String OPENRTB_VERSION = "2.6";

    private final String endpoint;
    private final JacksonMapper mapper;

    public SynapseHXBidder(String endpoint, JacksonMapper mapper) {
        this.endpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final BidRequest filteredBidRequest = bidRequest.toBuilder()
                .imp(bidRequest.getImp()
                        .stream()
                        .map(imp -> validateImp(imp, errors))
                        .filter(Objects::nonNull)
                        .toList())
                .build();

        if (filteredBidRequest.getImp().isEmpty()) {
            return Result.withErrors(errors);
        }

        final Imp firstImp = filteredBidRequest.getImp().getFirst();
        final String tenantId;

        try {
            tenantId = mapper.mapper()
                    .convertValue(firstImp.getExt(), SYNAPSE_HX_EXT_TYPE_REFERENCE)
                    .getBidder()
                    .getTenantId();
        } catch (IllegalArgumentException e) {
            return Result.withError(BidderError.badInput("Failed to parse bidder parameters"));
        }

        final MultiMap headers = HttpUtil.headers();

        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);

        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(endpoint);
        } catch (URISyntaxException e) {
            return Result.withError(BidderError.badInput("Invalid endpoint URI"));
        }

        return Result.of(List.of(BidderUtil.defaultRequest(filteredBidRequest,
                headers, uriBuilder.addParameter("pid", tenantId).toString(), mapper)), errors);
    }

    private static Imp validateImp(Imp imp, List<BidderError> errors) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            errors.add(BidderError.badInput(
                    "imp[%s]: Unsupported media type, bidder supports only banner and video".formatted(imp.getId())));
            return null;
        }
        if (imp.getXNative() != null || imp.getAudio() != null) {
            return imp.toBuilder().xNative(null).audio(null).build();
        }
        return imp;
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
        if (bidType == null) {
            return null;
        }

        return BidderBid.of(bid, bidType, bidResponse.getCur());
    }

    private BidType getBidType(Bid bid, List<BidderError> errors) {
        final Integer mType = bid.getMtype();
        if (mType != null) {
            return switch (mType) {
                case 1 -> BidType.banner;
                case 2 -> BidType.video;
                default -> {
                    errors.add(BidderError.badServerResponse("Unsupported media type: %d".formatted(mType)));
                    yield null;
                }
            };
        }

        final BidType bidType = Optional.ofNullable(bid.getExt())
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
