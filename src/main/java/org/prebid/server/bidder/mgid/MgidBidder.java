package org.prebid.server.bidder.mgid;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.mgid.model.ExtBidMgid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.mgid.ExtImpMgid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MgidBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MgidBidder(String endpoint, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> imps = new ArrayList<>();
        String accountId = null;
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpMgid impExt = parseImpExt(imp);

                if (StringUtils.isBlank(accountId) && StringUtils.isNotBlank(impExt.getAccountId())) {
                    accountId = impExt.getAccountId();
                }

                final Imp modifiedImp = modifyImp(imp, impExt);
                imps.add(modifiedImp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        if (StringUtils.isBlank(accountId)) {
            return Result.withError(BidderError.badInput("accountId is not set"));
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .tmax(bidRequest.getTmax())
                .imp(imps)
                .build();

        final String body = mapper.encode(outgoingRequest);

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl + accountId)
                .body(body)
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .build());
    }

    private ExtImpMgid parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt().get("bidder"), ExtImpMgid.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpMgid impExt) throws PreBidException {
        final ImpBuilder impBuilder = imp.toBuilder();

        final String cur = getCur(impExt);
        if (StringUtils.isNotBlank(cur)) {
            impBuilder.bidfloorcur(cur);
        }

        final BigDecimal bidFloor = getBidFloor(impExt);
        if (bidFloor != null) {
            impBuilder.bidfloor(bidFloor);
        }

        return impBuilder
                .tagid(getTagid(imp, impExt))
                .build();
    }

    private static String getTagid(Imp imp, ExtImpMgid impMgid) {
        final String placementId = impMgid.getPlacementId();
        final String impId = imp.getId();

        return StringUtils.isBlank(placementId) ? impId : String.format("%s/%s", placementId, impId);
    }

    private static String getCur(ExtImpMgid impMgid) {
        return ObjectUtils.defaultIfNull(
                currencyValueOrNull(impMgid.getCurrency()), currencyValueOrNull(impMgid.getCur()));
    }

    private static BigDecimal getBidFloor(ExtImpMgid impMgid) {
        return ObjectUtils.defaultIfNull(
                bidFloorValueOrNull(impMgid.getBidfloor()), bidFloorValueOrNull(impMgid.getBidFloorSecond()));
    }

    private static String currencyValueOrNull(String value) {
        return StringUtils.isNotBlank(value) && !value.equals("USD") ? value : null;
    }

    private static BigDecimal bidFloorValueOrNull(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall,
                                                  BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private BidType getBidType(Bid bid) {
        final ExtBidMgid bidExt = getBidExt(bid);
        if (bidExt == null) {
            return BidType.banner;
        }

        final BidType crtype = bidExt.getCrtype();
        return crtype == null ? BidType.banner : crtype;
    }

    private ExtBidMgid getBidExt(Bid bid) {
        try {
            return mapper.mapper().convertValue(bid.getExt(), ExtBidMgid.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

