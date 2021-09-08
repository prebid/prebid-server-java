package org.prebid.server.bidder.lockerdome;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.lockerdome.ExtImpLockerdome;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LockerdomeBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLockerdome>> LOCKERDOME_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpLockerdome>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LockerdomeBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final List<Imp> requestImps = bidRequest.getImp();
        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : requestImps) {
            try {
                validImps.add(validateImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid or supported impressions in the bid request."));
            return Result.withErrors(errors);
        }

        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        final BidRequest outgoingRequest = validImps.size() != requestImps.size()
                ? bidRequest.toBuilder().imp(validImps).build()
                : bidRequest;

        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(headers)
                        .body(body)
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    private Imp validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException("LockerDome does not currently support non-banner types.");
        }

        final ExtImpLockerdome extImpLockerdome;
        try {
            extImpLockerdome = mapper.mapper().convertValue(imp.getExt(), LOCKERDOME_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        if (StringUtils.isBlank(extImpLockerdome.getAdUnitId())) {
            throw new PreBidException("ext.bidder.adUnitId is empty.");
        }
        return imp;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }
}
