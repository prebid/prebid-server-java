package org.prebid.server.bidder.zeta_global_ssp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.zeta_global_ssp.ExtImpZetaGlobalSSP;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ZetaGlobalSspBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpZetaGlobalSSP>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String SID_MACRO = "{{AccountID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ZetaGlobalSspBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Imp firstImp = request.getImp().getFirst();
        final ExtImpZetaGlobalSSP extImp;

        try {
            extImp = parseImpExt(firstImp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest cleanedRequest = removeImpsExt(request);

        final HttpRequest<BidRequest> httpRequest =
                BidderUtil.defaultRequest(cleanedRequest, resolveEndpoint(extImp), mapper);

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpZetaGlobalSSP parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private String resolveEndpoint(ExtImpZetaGlobalSSP extImpZetaGlobalSSP) {
        return endpointUrl
                .replace(SID_MACRO, StringUtils.defaultString(String.valueOf(extImpZetaGlobalSSP.getSid())));
    }

    private BidRequest removeImpsExt(BidRequest request) {
        final List<Imp> imps = new ArrayList<>(request.getImp());
        final Imp firstImp = imps.getFirst().toBuilder().ext(null).build();
        imps.set(0, firstImp);

        return request.toBuilder()
                .imp(imps)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType mediaType = getMediaType(bid, errors);
        return mediaType == null ? null : BidderBid.of(bid, mediaType, currency);
    }

    private BidType getMediaType(Bid bid, List<BidderError> errors) {
        try {
            return Optional.ofNullable(bid.getExt())
                    .map(ext -> mapper.mapper().convertValue(ext, EXT_PREBID_TYPE_REFERENCE))
                    .map(ExtPrebid::getPrebid)
                    .map(ExtBidPrebid::getType)
                    .orElseThrow(IllegalArgumentException::new);
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badServerResponse(
                    "Failed to parse impression \"%s\" mediatype".formatted(bid.getImpid())));
            return null;
        }
    }
}
