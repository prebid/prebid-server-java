package org.prebid.server.bidder.smilewanted;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.smilewanted.ExtImpSmilewanted;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SmileWantedBidder implements Bidder<BidRequest> {

    private static final String SW_INTEGRATION_TYPE = "prebid_server";
    private static final String X_OPENRTB_VERSION = "2.5";
    private static final int DEFAULT_AT = 1;
    private static final String ZONE_ID_MACRO = "{{ZoneId}}";

    private static final TypeReference<ExtPrebid<?, ExtImpSmilewanted>> SMILEWANTED_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SmileWantedBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpSmilewanted extImpSmilewanted;

        try {
            extImpSmilewanted = parseImpExt(request.getImp().getFirst());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest outgoingRequest = request.toBuilder().at(DEFAULT_AT).build();
        final String url = endpointUrl.replace(ZONE_ID_MACRO, HttpUtil.encodeUrl(extImpSmilewanted.getZoneId()));

        return Result.withValue(BidderUtil.defaultRequest(
                outgoingRequest,
                createHeaders(),
                url,
                mapper));
    }

    private ExtImpSmilewanted parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SMILEWANTED_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private static MultiMap createHeaders() {
        return HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION)
                .add("sw-integration-type", SW_INTEGRATION_TYPE);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final SeatBid firstSeatBid = bidResponse.getSeatbid().getFirst();
        return CollectionUtils.emptyIfNull(firstSeatBid.getBid()).stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(String impid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impid) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }
}
