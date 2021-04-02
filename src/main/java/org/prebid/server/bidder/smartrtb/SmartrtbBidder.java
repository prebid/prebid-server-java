package org.prebid.server.bidder.smartrtb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.smartrtb.model.SmartrtbResponseExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.smartrtb.ExtImpSmartrtb;
import org.prebid.server.proto.openrtb.ext.request.smartrtb.ExtRequestSmartrtb;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SmartRTB {@link Bidder} implementation.
 */
public class SmartrtbBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmartrtb>> SMARTRTB_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSmartrtb>>() {
            };

    private static final String CREATIVE_TYPE_BANNER = "BANNER";
    private static final String CREATIVE_TYPE_VIDEO = "VIDEO";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SmartrtbBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        String pubId = null;

        for (Imp imp : request.getImp()) {
            try {
                final Imp validImp = validateImp(imp);
                final ExtImpSmartrtb extImp = parseImpExt(imp);

                if (StringUtils.isBlank(pubId) && StringUtils.isNoneEmpty(extImp.getPubId())) {
                    pubId = extImp.getPubId();
                }

                final String zoneId = extImp.getZoneId();
                final Imp updatedImp = StringUtils.isNotEmpty(zoneId)
                        ? validImp.toBuilder().tagid(zoneId).build()
                        : imp;
                validImps.add(updatedImp);

            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (StringUtils.isEmpty(pubId)) {
            errors.add(BidderError.badInput("Cannot infer publisher ID from bid ext"));
            return Result.of(null, errors);
        } else {
            ExtRequestSmartrtb.of(pubId, null, null);
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();
        final String body = mapper.encode(outgoingRequest);
        final String requestUrl = endpointUrl + pubId;
        final MultiMap headers = HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(requestUrl)
                        .headers(headers)
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private Imp validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException("SmartRTB only supports banner and video");
        }
        return imp;
    }

    private ExtImpSmartrtb parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SMARTRTB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderBid> bidderBids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                final ObjectNode ext = bid.getExt();
                final SmartrtbResponseExt smartrtbResponseExt;
                try {
                    smartrtbResponseExt = parseResponseExt(ext);
                } catch (PreBidException e) {
                    return Result.withError(BidderError.badServerResponse("Invalid bid extension from endpoint."));
                }
                final BidType bidType;
                switch (smartrtbResponseExt.getFormat()) {
                    case CREATIVE_TYPE_BANNER:
                        bidType = BidType.banner;
                        break;
                    case CREATIVE_TYPE_VIDEO:
                        bidType = BidType.video;
                        break;
                    default:
                        return Result.withError(BidderError.badServerResponse(String.format(
                                "Unsupported creative type %s.", smartrtbResponseExt.getFormat())));
                }
                final Bid updatedBid = bid.toBuilder().ext(null).build();
                final BidderBid bidderBid = BidderBid.of(updatedBid, bidType, bidResponse.getCur());
                bidderBids.add(bidderBid);
            }
        }
        return Result.withValues(bidderBids);
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private SmartrtbResponseExt parseResponseExt(ObjectNode ext) {
        if (ext == null) {
            throw new PreBidException("Invalid bid extension from endpoint.");
        }
        try {
            return mapper.mapper().treeToValue(ext, SmartrtbResponseExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }
}
