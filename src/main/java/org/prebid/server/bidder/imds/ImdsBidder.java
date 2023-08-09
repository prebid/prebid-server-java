package org.prebid.server.bidder.imds;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.imds.ExtImpImds;
import org.prebid.server.proto.openrtb.ext.request.imds.ExtRequestImds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ImdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpImds>> IMDS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final String prebidVersion;
    private final JacksonMapper mapper;

    public ImdsBidder(String endpointUrl, String prebidVersion, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.prebidVersion = Objects.requireNonNull(prebidVersion);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        ExtImpImds firstExtImp = null;

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpImds extImpImds;
            try {
                extImpImds = parseAndValidateExtImp(imp.getExt());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput("Invalid Impression: " + e.getMessage()));
                continue;
            }

            final Imp updatedImp = imp.toBuilder().tagid(extImpImds.getTagId()).build();
            validImps.add(updatedImp);

            if (firstExtImp == null) {
                firstExtImp = extImpImds;
            }
        }

        if (validImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(validImps)
                .ext(mapper.fillExtension(ExtRequest.empty(), ExtRequestImds.of(firstExtImp.getSeatId())))
                .build();

        return Result.of(
                Collections.singletonList(
                        BidderUtil.defaultRequest(
                            outgoingRequest,
                            generateEndpointUrl(firstExtImp),
                            mapper
                    )
                ),
                errors
        );
    }

    private String generateEndpointUrl(ExtImpImds firstExtImp) {
        final String accountId = URLEncoder.encode(firstExtImp.getSeatId(), StandardCharsets.UTF_8);
        final String sourceId = URLEncoder.encode(prebidVersion, StandardCharsets.UTF_8);
        return endpointUrl
                .replaceAll("\\{\\{AccountID}}", accountId)
                .replaceAll("\\{\\{SourceId}}", sourceId);
    }

    private ExtImpImds parseAndValidateExtImp(ObjectNode impExt) {
        final ExtImpImds extImp = parseExtImp(impExt);

        if (StringUtils.isBlank(extImp.getSeatId()) || StringUtils.isBlank(extImp.getTagId())) {
            throw new PreBidException("imp.ext has no seatId or tagId");
        }

        return extImp;
    }

    private ExtImpImds parseExtImp(ObjectNode impExt) {
        try {
            return mapper.mapper().convertValue(impExt, IMDS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> mapBidToBidderBid(bid, bidRequest.getImp(), bidResponse.getCur()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid mapBidToBidderBid(Bid bid, List<Imp> imps, String currency) {
        final BidType bidType = getBidType(bid.getImpid(), imps);

        if (bidType == BidType.banner || bidType == BidType.video) {
            return BidderBid.of(bid, bidType, currency);
        }
        return null;
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (final Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                if (imp.getAudio() != null) {
                    return BidType.audio;
                }
            }
        }
        return BidType.banner;
    }
}
