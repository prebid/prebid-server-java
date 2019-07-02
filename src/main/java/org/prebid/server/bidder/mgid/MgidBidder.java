package org.prebid.server.bidder.mgid;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.mgid.model.BidExtResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.mgid.ExtImpMgid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MgidBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private final String mgidEndpoint;

    public MgidBidder(String endpoint) {
        mgidEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
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
                return Result.emptyWithError(BidderError.badInput(e.getMessage()));
            }
        }

        if (StringUtils.isBlank(accountId)) {
            return Result.emptyWithError(BidderError.badInput("accountId is not set"));
        }

        Long tmax = bidRequest.getTmax();
        if (tmax == null || tmax.equals(0L)) {
            tmax = 200L;
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .tmax(tmax)
                .imp(imps)
                .build();

        final String body = Json.encode(outgoingRequest);

        return Result.of(Collections.singletonList(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(mgidEndpoint + accountId)
                .body(body)
                .headers(BidderUtil.headers())
                .payload(outgoingRequest)
                .build()), Collections.emptyList());
    }

    private Imp modifyImp(Imp imp, ExtImpMgid impExt) throws PreBidException {
        final ImpBuilder impBuilder = imp.toBuilder();
        final String cur = getCur(impExt);
        final BigDecimal bidFlor = getBidFloor(impExt);

        if (StringUtils.isNotBlank(cur)) {
            impBuilder.bidfloorcur(cur);
        }
        if (bidFlor != null) {
            impBuilder.bidfloor(bidFlor);
        }

        return impBuilder
                .tagid(impExt.getPlacementId())
                .build();
    }

    private String getCur(ExtImpMgid impMgid) {
        if (StringUtils.isNotBlank(impMgid.getCurrency()) && !impMgid.getCurrency().equals("USD")) {
            return impMgid.getCurrency();
        }
        if (StringUtils.isNotBlank(impMgid.getCur()) && !impMgid.getCur().equals("USD")) {
            return impMgid.getCur();
        }
        return "";
    }

    private BigDecimal getBidFloor(ExtImpMgid impMgid) {
        BigDecimal bidfloor = impMgid.getBidfloor();
        if (bidfloor == null || bidfloor.compareTo(BigDecimal.ZERO) <= 0) {
            bidfloor = impMgid.getBidFloorSecond();
        }
        if (bidfloor != null && bidfloor.compareTo(BigDecimal.ZERO) > 0) {
            return bidfloor;
        }
        return null;
    }

    private ExtImpMgid parseImpExt(Imp imp) {
        try {
            return Json.mapper.convertValue(imp.getExt().get("bidder"), ExtImpMgid.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall,
                                                  BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json
                    .decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse),
                    Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
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
                .map(bid -> BidderBid.of(bid, getBidType(bid), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private BidType getBidType(Bid bid) {
        BidExtResponse bidExt = getBidExt(bid);

        if (bidExt == null || StringUtils.isBlank(bidExt.getCrtype())) {
            return BidType.banner;
        }

        String crtype = bidExt.getCrtype();
        return Arrays.stream(BidType.values())
                .filter(bidType -> bidType.name().equalsIgnoreCase(crtype))
                .findFirst()
                .orElse(BidType.banner);
    }

    private BidExtResponse getBidExt(Bid bid) {
        try {
            return Json.mapper.convertValue(bid.getExt(), BidExtResponse.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public final Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
