package org.prebid.server.bidder.logicad;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.logicad.ExtImpLogicad;
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

public class LogicadBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLogicad>> LOGICAD_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpLogicad>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LogicadBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        try {
            final Map<ExtImpLogicad, List<Imp>> impToExtImp = getImpToExtImp(request, errors);
            httpRequests.addAll(buildAdapterRequests(request, impToExtImp));
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), errors);
        }

        return Result.of(httpRequests, errors);
    }

    private Map<ExtImpLogicad, List<Imp>> getImpToExtImp(BidRequest request, List<BidderError> errors) {
        final Map<ExtImpLogicad, List<Imp>> extToListOfUpdatedImp = new HashMap<>();
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpLogicad extImpLogicad = parseAndValidateImpExt(imp);
                final Imp updatedImp = updateImp(imp, extImpLogicad.getTid());

                extToListOfUpdatedImp.putIfAbsent(extImpLogicad, new ArrayList<>());
                extToListOfUpdatedImp.get(extImpLogicad).add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (extToListOfUpdatedImp.isEmpty()) {
            throw new PreBidException("No appropriate impressions");
        }

        return extToListOfUpdatedImp;
    }

    private ExtImpLogicad parseAndValidateImpExt(Imp imp) {
        final ExtImpLogicad bidder;
        try {
            bidder = mapper.mapper().convertValue(imp.getExt(), LOGICAD_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(bidder.getTid())) {
            throw new PreBidException("No tid value provided");
        }

        return bidder;
    }

    private static Imp updateImp(Imp imp, String tid) {
        return imp.toBuilder().tagid(tid).ext(null).build();
    }

    private List<HttpRequest<BidRequest>> buildAdapterRequests(BidRequest bidRequest,
                                                               Map<ExtImpLogicad, List<Imp>> impExtToListOfImps) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Map.Entry<ExtImpLogicad, List<Imp>> impExtAndListOfImps : impExtToListOfImps.entrySet()) {
            final BidRequest updatedBidRequest = BidRequest.builder().imp(impExtAndListOfImps.getValue()).build();
            final String body = mapper.encode(updatedBidRequest);
            final HttpRequest<BidRequest> createdBidRequest = HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(endpointUrl)
                    .body(body)
                    .headers(HttpUtil.headers())
                    .payload(bidRequest)
                    .build();

            httpRequests.add(createdBidRequest);
        }

        return httpRequests;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
