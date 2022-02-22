package org.prebid.server.bidder.engagebdr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.engagebdr.ExtImpEngagebdr;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class EngagebdrBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpEngagebdr>> ENGAGEBDR_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public EngagebdrBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<String, List<Imp>> dispatchedRequest = dispatchImpsBySspid(bidRequest, errors);

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        for (Map.Entry<String, List<Imp>> sspidToImpsEntry : dispatchedRequest.entrySet()) {
            final BidRequest updatedBidRequest = bidRequest.toBuilder().imp(sspidToImpsEntry.getValue()).build();

            httpRequests.add(HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(endpointUrl + "?zoneid=" + sspidToImpsEntry.getKey())
                    .body(mapper.encodeToBytes(updatedBidRequest))
                    .headers(HttpUtil.headers())
                    .payload(updatedBidRequest)
                    .build());
        }

        return Result.of(httpRequests, errors);
    }

    private Map<String, List<Imp>> dispatchImpsBySspid(BidRequest bidRequest, List<BidderError> errors) {
        final Map<String, List<Imp>> sspidToImp = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                validateImp(imp);
                final String sspid = parseImpExt(imp).getSspid();
                validateSspid(sspid);

                sspidToImp.computeIfAbsent(sspid, key -> new ArrayList<>()).add(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(String.format("Ignoring imp id=%s, %s", imp.getId(), e.getMessage())));
            }
        }
        return sspidToImp;
    }

    private static void validateImp(Imp imp) {
        if (imp.getAudio() != null) {
            throw new PreBidException("invalid MediaType EngageBDR only supports Banner, Video and Native");
        }
    }

    private static void validateSspid(String sspid) {
        if (StringUtils.isBlank(sspid)) {
            throw new PreBidException("no sspid present");
        }
    }

    private ExtImpEngagebdr parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(),
                    ENGAGEBDR_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("error while decoding impExt, err: %s", e.getMessage()));
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, bidRequest));
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
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}

