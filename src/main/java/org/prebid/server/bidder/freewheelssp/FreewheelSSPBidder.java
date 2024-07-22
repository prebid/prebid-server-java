package org.prebid.server.bidder.freewheelssp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.proto.openrtb.ext.request.freewheelssp.ExtImpFreewheelSSP;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class FreewheelSSPBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpFreewheelSSP>> FREEWHEELSSP_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String COMPONENT_ID_HEADER_NAME = "Componentid";
    private static final String COMPONENT_ID_HEADER_VALUE = "prebid-java";
    private static final BidType BID_TYPE = BidType.video;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public FreewheelSSPBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> modifiedImps = new ArrayList<>();

        try {
            for (final Imp imp : bidRequest.getImp()) {
                final ExtImpFreewheelSSP extImpFreewheelSSP = parseExtImp(imp);
                modifiedImps.add(modifyImp(imp, extImpFreewheelSSP));
            }
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }


        return Result.withValue(
                BidderUtil.defaultRequest(
                        modifyBidRequest(bidRequest, modifiedImps),
                        headers(),
                        endpointUrl,
                        mapper));
    }

    private ExtImpFreewheelSSP parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), FREEWHEELSSP_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "Invalid imp.ext for impression id %s. Error Infomation: %s"
                            .formatted(imp.getId(), e.getMessage()));
        }
    }

    private Imp modifyImp(Imp imp, ExtImpFreewheelSSP extImpFreewheelSSP) {
        return imp.toBuilder()
                .ext(mapper.mapper().valueToTree(extImpFreewheelSSP))
                .build();
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, List<Imp> imps) {
        return bidRequest.toBuilder().imp(imps).build();
    }

    private static MultiMap headers() {
        return HttpUtil.headers().add(COMPONENT_ID_HEADER_NAME, COMPONENT_ID_HEADER_VALUE);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.builder()
                        .bid(bid)
                        .type(BID_TYPE)
                        .bidCurrency(bidResponse.getCur())
                        .videoInfo(videoInfo(bid))
                        .build())
                .toList();
    }

    private static ExtBidPrebidVideo videoInfo(Bid bid) {
        final List<String> cat = bid.getCat();
        final Integer duration = bid.getDur();

        final boolean catNotEmpty = CollectionUtils.isNotEmpty(cat);
        final boolean durationValid = duration != null && duration > 0;
        return catNotEmpty || durationValid
                ? ExtBidPrebidVideo.of(
                durationValid ? duration : null,
                catNotEmpty ? cat.getFirst() : null)
                : null;
    }
}
