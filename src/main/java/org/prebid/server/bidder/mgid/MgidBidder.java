package org.prebid.server.bidder.mgid;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.ImpWithExt;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.mgid.ExtImpMgid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MgidBidder implements Bidder<BidRequest> {

    private static final RequestCreationStrategy MGID_REQUEST_STRATEGY = RequestCreationStrategy.REQUEST_PER_IMP;
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private final String mgidEndpoint;

    public MgidBidder(String endpoint) {
        mgidEndpoint = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<ImpWithExt<ExtImpMgid>> modifiedImpsWithExts = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpMgid impExt = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, impExt);
                modifiedImpsWithExts.add(new ImpWithExt<>(modifiedImp, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (modifiedImpsWithExts.isEmpty()) {
            return Result.of(Collections.emptyList(), errors);
        }
        return Result.of(createHttpRequests(bidRequest, modifiedImpsWithExts), errors);
    }

    private Imp modifyImp(Imp imp, ExtImpMgid impExt) throws PreBidException {
        ImpBuilder impBuilder = imp.toBuilder();
        String cur = getCur(impExt);
        BigDecimal bidFlor = getBidFlor(impExt);

        if (cur != null && !cur.equals("USD")) {
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
        return ObjectUtils.defaultIfNull(impMgid.getCur(), impMgid.getCurrency());
    }

    private BigDecimal getBidFlor(ExtImpMgid impMgid) {
        return ObjectUtils.defaultIfNull(impMgid.getBidfloor(), impMgid.getBidFlor());
    }

    private ExtImpMgid parseImpExt(Imp imp) {
        try {
            return Json.mapper.convertValue(imp.getExt().get("bidder"), ExtImpMgid.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private List<HttpRequest<BidRequest>> createHttpRequests(BidRequest bidRequest,
                                                             List<ImpWithExt<ExtImpMgid>> impsWithExts) {
        switch (MGID_REQUEST_STRATEGY) {
            case REQUEST_PER_IMP:
                return impsWithExts.stream()
                        .map(impWithExt -> makeSingleRequest(impWithExt, bidRequest))
                        .collect(Collectors.toList());
            case SINGLE_REQUEST:
                return makeBatchRequest(bidRequest, impsWithExts);
            default:
                throw new IllegalArgumentException(
                        String.format("Invalid request creation strategy: %s",
                                MGID_REQUEST_STRATEGY));
        }
    }

    private HttpRequest<BidRequest> makeSingleRequest(ImpWithExt<ExtImpMgid> impWithExt,
                                                      BidRequest bidRequest) {
        return makeRequest(createEndpointFromAccountId(impWithExt), bidRequest,
                Collections.singletonList(impWithExt.getImp()));
    }

    private List<HttpRequest<BidRequest>> makeBatchRequest(BidRequest bidRequest,
                                                           List<ImpWithExt<ExtImpMgid>> impsWithExts) {
        return splitIntoMapOfImpByEndpoint(impsWithExts).entrySet().stream()
                .map(impWithExtByEndpoint -> makeRequest(impWithExtByEndpoint.getKey(), bidRequest,
                        impWithExtByEndpoint.getValue()))
                .collect(Collectors.toList());
    }

    private Map<String, List<Imp>> splitIntoMapOfImpByEndpoint(
            List<ImpWithExt<ExtImpMgid>> impsWithExts) {
        return impsWithExts.stream()
                .collect(Collectors.groupingBy(this::createEndpointFromAccountId,
                        Collectors.mapping(ImpWithExt::getImp, Collectors.toList())));
    }

    private String createEndpointFromAccountId(ImpWithExt<ExtImpMgid> mgidExt) {
        return mgidEndpoint + mgidExt.getImpExt().getAccountId();
    }

    private HttpRequest<BidRequest> makeRequest(String endpoint, BidRequest bidRequest,
                                                List<Imp> imps) {
        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(imps)
                .build();

        final String body = Json.encode(outgoingRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpoint)
                .body(body)
                .headers(BidderUtil.headers())
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall,
                                                  BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json
                    .decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse),
                    Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid
                        .of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), getBidCurrency()))
                .collect(Collectors.toList());
    }

    private BidType getBidType(String impId, List<Imp> imps) {
        BidType bidType = BidType.banner;
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return bidType;
                } else if (imp.getVideo() != null) {
                    bidType = BidType.video;
                } else if (imp.getXNative() != null) {
                    bidType = BidType.xNative;
                } else if (imp.getAudio() != null) {
                    bidType = BidType.audio;
                }
            }
        }
        return bidType;
    }

    private String getBidCurrency() {
        return DEFAULT_BID_CURRENCY;
    }

    @Override
    public final Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    public enum RequestCreationStrategy {
        SINGLE_REQUEST,
        REQUEST_PER_IMP
    }
}
